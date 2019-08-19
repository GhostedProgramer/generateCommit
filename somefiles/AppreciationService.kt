package com.musicbible.service

import com.boostfield.aliyun.green.AliyunGreenService
import com.boostfield.aliyun.green.ScanTextResult
import com.boostfield.aliyun.green.SpamTextLabel
import com.boostfield.aliyun.green.SuggestionEnum
import com.boostfield.extension.string.containAnyChinese
import com.boostfield.extension.string.removePunct
import com.boostfield.extension.toDate
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.extension.withSortings
import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.CompletionSuggestService
import com.boostfield.spring.service.DraftableService
import com.boostfield.spring.service.KeyOption
import com.boostfield.spring.service.SearchService
import com.musicbible.aspect.Locked
import com.musicbible.es.model.EsAppreciation
import com.musicbible.es.repository.AppreciationEsRepository
import com.musicbible.event.AppreciationNotificationEvent
import com.musicbible.event.DeleteTimeLineEvent
import com.musicbible.event.EntityEvent
import com.musicbible.event.TimeLineEvent
import com.musicbible.extension.transform
import com.musicbible.mapper.appreciation.AppreciationBackendListInput
import com.musicbible.mapper.appreciation.AppreciationMapper
import com.musicbible.mapper.appreciation.CreateAppreciationInput
import com.musicbible.mapper.appreciation.Key
import com.musicbible.mapper.appreciation.RecommendListInput
import com.musicbible.mapper.appreciation.UpdateAppreciationInput
import com.musicbible.model.Appreciation
import com.musicbible.model.ModelEnum
import com.musicbible.model.QAppreciation
import com.musicbible.model.User
import com.musicbible.repository.base.AppreciationRepository
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MultiMatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.RangeQueryBuilder
import org.elasticsearch.search.sort.SortBuilder
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.query.FetchSourceFilterBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface AppreciationService :
    DraftableService<Appreciation>, AppreciationRepository, SearchService<EsAppreciation, UUID>, CompletionSuggestService {
    override val modelName: String
        get() = "长评"

    fun next(id: UUID): Appreciation?

    fun nextWithSort(id: UUID, sort: String): Appreciation?

    fun findNext(appreciation: Appreciation): Appreciation?

    fun findNextWithSort(appreciation: Appreciation, sort: String): Appreciation?

    fun create(targetType: ModelEnum, targetId: UUID, input: CreateAppreciationInput): Appreciation

    fun update(id: UUID, body: UpdateAppreciationInput)

    fun read(id: UUID): Appreciation

    fun listForTargetPublished(targetId: UUID, pageable: Pageable): Page<Appreciation>

    fun listPublished(pageable: Pageable): Page<Appreciation>

    fun listForType(targetType: ModelEnum, pageable: Pageable): Page<Appreciation>

    fun listCreatedByPublished(createdBy: User, pageable: Pageable): Page<Appreciation>

    fun listCreatedBy(createdBy: User, pageable: Pageable): Page<Appreciation>

    fun updateCommendLevel(id: UUID, commendLevel: Int)

    fun listBackend(input: AppreciationBackendListInput): Page<Appreciation>

    fun recommendList(input: RecommendListInput): Page<Appreciation>

    fun nextEsAppreciation(id: UUID, sort: String, q: String?, appreciation: Appreciation?): Appreciation?

    fun nextAppreciationForRecommend(id: UUID, input: RecommendListInput): Appreciation?

    /*ES同步*/

    fun indexToEs(appreciation: Appreciation)

    fun indexToEs(appreciation: List<Appreciation>)
    fun createAndCheck(targetType: ModelEnum, targetId: UUID, input: CreateAppreciationInput, user: User): ScanTextResult

    fun updateAndCheck(id: UUID, body: UpdateAppreciationInput, user: User): ScanTextResult

    /**
     * 删除用户创建的长评
     */
    fun deleteUserCreated(user: User, id: UUID, reason: String)

    fun asyncIndexToEs(appreciation: List<Appreciation>): CompletableFuture<Unit>

    fun afterPublishedBeDelete(target: Appreciation)
}

@Service
@Transactional
class AppreciationServiceImpl(
    @Autowired val appreciationRepository: AppreciationRepository,
    @Autowired val releaseService: ReleaseService,
    @Autowired val workService: WorkService,
    @Autowired val artistService: ArtistService,
    @Autowired val appreciationEsRepository: AppreciationEsRepository,
    @Autowired val esIndexService: EsIndexService<Appreciation, EsAppreciation>,
    @Autowired val aliyunGreenService: AliyunGreenService,
    @Autowired val appreciationMapper: AppreciationMapper,
    @Autowired val userService: UserService,
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : AppreciationService, AppreciationRepository by appreciationRepository {
    val qAppreciation: QAppreciation = QAppreciation.appreciation

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateAndCheck(id: UUID, body: UpdateAppreciationInput, user: User): ScanTextResult {
        val appreciation = findExistsOrThrow(id)
        val scanTextResult = aliyunGreenService.scanText("标题:${body.title} 内容:${body.content}", user.stringId)
        return if (scanTextResult.suggestion == SuggestionEnum.pass) {
            body.title?.also { appreciation.title = it }
            body.content?.also { appreciation.content = it }
            appreciation.published = body.published?.let { it } ?: true    //为兼容前台代码
            save(appreciation).also {
                eventPublisher.publishEvent(EntityEvent.updated(it))
                if (appreciation.published) {
                    eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Appreciation, it.id, it.createdBy))
                }
            }
            /*通过aliyun内容安全服务检测是否有不良内容*/
            scanTextResult
        } else {
            throw AppError.Forbidden.default(msg = "您所上传的内容涉嫌含有${scanTextResult.label.getFeedbackText()}内容,请删除后重新发布")
        }
    }

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun createAndCheck(targetType: ModelEnum, targetId: UUID, input: CreateAppreciationInput, user: User): ScanTextResult {
        val scanTextResult = aliyunGreenService.scanText("标题:${input.title} 内容:${input.content}", user.stringId)
        return if (scanTextResult.suggestion == SuggestionEnum.pass) {
            val appreciation = Appreciation()
            appreciation.published = input.published?.let { it } ?: true    //为兼容前台代码
            appreciation.title = input.title
            appreciation.content = input.content
            appreciation.targetType = targetType
            appreciation.targetId = targetId
            save(appreciation).also {
                eventPublisher.publishEvent(EntityEvent.created(it))
                if (appreciation.published) {
                    eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Appreciation, it.id, it.createdBy))
                }
            }
            /*通过aliyun内容安全服务检测是否有不良内容*/
            scanTextResult
        } else {
            throw AppError.Forbidden.default(msg = "您所上传的内容涉嫌含有${scanTextResult.label.getFeedbackText()}内容,请删除后重新发布")
        }
    }

    @Locked("appreciation-%{#id}")
    override fun deleteUserCreated(user: User, id: UUID, reason: String) {
        val appreciation = findByDeletedFalseAndPublishedTrueAndId(id)
        softDelete(appreciation!!)

        // 同时删除动态
        eventPublisher.publishEvent(DeleteTimeLineEvent(appreciation.id))
        if (appreciation.createdBy.id == user.id) return    //删除自己发布的长评不用发送通知
        val event = AppreciationNotificationEvent(
            source = appreciation,
            targetId = appreciation.targetId,
            targetType = appreciation.targetType,
            extra = reason,
            createdBy = appreciation.createdBy
        )
        eventPublisher.publishEvent(event)
    }

    override fun nextAppreciationForRecommend(id: UUID, input: RecommendListInput): Appreciation? {
        val appreciations = recommendList(input).toMutableList()
        val appreciation = findExistsOrThrow(id)
        val index = appreciations.indexOf(appreciation)
        return if (index + 1 == appreciations.size)
            null
        else
            appreciations[index + 1]
    }

    override fun recommendList(input: RecommendListInput): Page<Appreciation> {
        val searchQuery = NativeSearchQueryBuilder()
        val booleanBuilder = BoolQueryBuilder()
        searchQuery.withQuery(getHomePageListQuery(booleanBuilder, input.q))
            .withPageable(input.pageable())
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
            .withSortings(input.sortings)
        if (input.q != null) {
            searchQuery.withMinScore(1.0f)
        }
        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsAppreciation::class.java)
            .transform(this)
    }

    private fun getHomePageListQuery(booleanBuilder: BoolQueryBuilder, q: String?): BoolQueryBuilder {
        booleanBuilder.filter(QueryBuilders.termsQuery("published", true))
        q.removePunct()
        return q?.let {
            booleanBuilder.should(
                QueryBuilders.multiMatchQuery(q, "title", "content", "title.py", "content.py")
                    .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
            ).should(
                if (q.containAnyChinese()) {
                    QueryBuilders.multiMatchQuery(q, "releaseInfo.titleCN")
                        .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                } else {
                    QueryBuilders.multiMatchQuery(q, "releaseInfo.title1", "releaseInfo.title2")
                        .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                }
            ).should(
                if (q.containAnyChinese()) {
                    QueryBuilders.multiMatchQuery(q, "artistInfo.nameCN", "artistInfo.abbrCN")
                        .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                } else {
                    QueryBuilders.multiMatchQuery(q, "artistInfo.firstName",
                        "artistInfo.lastName", "artistInfo.abbr").type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                }
            ).should(
                if (q.containAnyChinese()) {
                    QueryBuilders.multiMatchQuery(q, "workInfo.titleCN")
                        .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                } else {
                    QueryBuilders.multiMatchQuery(q, "workInfo.title", "workInfo.titleCN")
                        .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                }
            )
        } ?: booleanBuilder
    }

    override fun listBackend(input: AppreciationBackendListInput): Page<Appreciation> {
        var predicate = qAppreciation.deleted.isFalse.and(qAppreciation.published.isTrue)

        input.type?.also {
            predicate = predicate.and(qAppreciation.targetType.eq(it))
        }
        if (!input.q.isNullOrEmpty()) {
            if (input.key != null) {
                when (input.key) {
                    Key.TITLE ->
                        predicate = predicate.and(qAppreciation.title.contains(input.q))
                    Key.CREATEDBY ->
                        predicate = predicate.and(qAppreciation.createdBy.nickName.contains(input.q))
                }
            }
        }

        return findAll(predicate, input.defaultSortByCreateAt())
    }


    override fun next(id: UUID): Appreciation? {
        val appreciation = findOrThrow(id)
        return findNext(appreciation)
    }

    /**
     * 查找相关联的下一个长评
     */
    override fun findNext(appreciation: Appreciation) =
        findFirstByDeletedFalseAndPublishedTrueAndTargetIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            appreciation.targetId, appreciation.createdAt
        )

    override fun nextWithSort(id: UUID, sort: String): Appreciation? {
        val appreciation = findOrThrow(id)
        return findNextWithSort(appreciation, sort)
    }

    override fun findNextWithSort(appreciation: Appreciation, sort: String) =
        when (sort) {
            "-createdAt" -> findFirstByDeletedFalseAndPublishedTrueAndTargetIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                appreciation.targetId, appreciation.createdAt)
            "-likeCount" -> findByDeletedFalseAndPublishedTrueAndTargetIdOrderByLikeCountDesc(appreciation.targetId).let {
                val index = it.indexOf(appreciation)
                if ((index + 1) == it.size) {
                    null
                } else {
                    it[it.indexOf(appreciation) + 1]
                }
            }
            else -> throw IllegalArgumentException("sort参数错误")
        }

    override fun nextEsAppreciation(id: UUID, sort: String, q: String?, appreciation: Appreciation?): Appreciation? {
        if (appreciation == null) {
            throw AppError.NotFound.default(msg = "当前长评无法被找到")
        } else {
            val booleanBuilder = getHomePageListQuery(BoolQueryBuilder(), q)
            val searchQuery = NativeSearchQueryBuilder()
            return when (sort) {
                "-likeCount" -> {
                    searchQuery.withQuery(booleanBuilder)
                        .withSortings(mutableListOf<SortBuilder<*>>(SortBuilders.scoreSort().order(SortOrder.DESC))
                            .also {
                                it.addAll(mutableListOf(SortBuilders.fieldSort(sort.substring(1)).order(SortOrder.DESC)))
                            })
                    val list = if (q == null) {
                        appreciationEsRepository.search(searchQuery.build()).toList()
                    } else {
                        appreciationEsRepository.search(searchQuery.withMinScore(1.0f).build()).toList()
                    }
                    val index = list.indexOf(appreciationEsRepository.findById(appreciation.id).get())
                    if ((index + 1) == list.size) {
                        null
                    } else {
                        findByDeletedFalseAndPublishedTrueAndId(list[index + 1].id)
                    }
                }
                "-createdAt" -> {
                    searchQuery.withQuery(booleanBuilder.filter(RangeQueryBuilder("createdAt").format("yyyyMMddHHmmss")
                        .lt(SimpleDateFormat("yyyyMMddHHmmss")
                            .format(appreciation.createdAt.minusHours(@Suppress("MagicNumber") 8).toDate()))))
                        .withPageable(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")))
                    val search = if (q == null) {
                        appreciationEsRepository.search(searchQuery.build())
                    } else {
                        appreciationEsRepository.search(searchQuery.withMinScore(1.0f).build())
                    }
                    return if (!search.isEmpty) {
                        val first = search.first()
                        findByDeletedFalseAndPublishedTrueAndId(first.id)
                    } else {
                        null
                    }
                }
                else -> throw IllegalArgumentException("排序参数非法")
            }
        }
    }

    override fun create(targetType: ModelEnum, targetId: UUID, input: CreateAppreciationInput): Appreciation {
        val appreciation = Appreciation()
        appreciation.title = input.title
        appreciation.content = input.content
        appreciation.targetType = targetType
        appreciation.targetId = targetId
        input.published?.also { appreciation.published = it }
        return save(appreciation).also {
            eventPublisher.publishEvent(EntityEvent.created(it))
            if (appreciation.published) {
                eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Appreciation, appreciation.id, appreciation.createdBy))
            }
        }
    }

    override fun update(id: UUID, body: UpdateAppreciationInput) {
        val appreciation = findExistsOrThrow(id)
        body.title?.also { appreciation.title = it }
        body.content?.also { appreciation.content = it }
        body.published?.also { appreciation.published = it }
        save(appreciation).also {
            eventPublisher.publishEvent(EntityEvent.updated(it))
            if (appreciation.published) {
                eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Appreciation, appreciation.id, appreciation.createdBy))
            }
        }
    }

    override fun read(id: UUID): Appreciation {
        var appreciation = findExistsOrThrow(id)
        appreciation.viewCount++
        appreciation = save(appreciation)
        eventPublisher.publishEvent(EntityEvent.updated(appreciation))
        return appreciation
    }

    override fun listForTargetPublished(targetId: UUID, pageable: Pageable): Page<Appreciation> {
        return findByDeletedFalseAndPublishedTrueAndTargetId(targetId, pageable)
    }

    override fun listForType(targetType: ModelEnum, pageable: Pageable): Page<Appreciation> {
        return findByDeletedFalseAndTargetType(targetType, pageable)
    }

    override fun listPublished(pageable: Pageable): Page<Appreciation> {
        return findByDeletedFalseAndPublishedTrue(pageable)
    }

    override fun listCreatedByPublished(createdBy: User, pageable: Pageable): Page<Appreciation> {
        return findByDeletedFalseAndPublishedTrueAndCreatedBy(createdBy, pageable)
    }

    override fun listCreatedBy(createdBy: User, pageable: Pageable): Page<Appreciation> {
        return findByDeletedFalseAndCreatedBy(createdBy, pageable)
    }

    @Locked("appreciation-%{#id}")
    override fun updateCommendLevel(id: UUID, commendLevel: Int) {
        val appreciation = findExistsOrThrow(id)
        appreciation.commendLevel = commendLevel
        save(appreciation).also {
            eventPublisher.publishEvent(EntityEvent.updated(it))
        }
    }

    override fun completionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("appreciation",
            listOf("nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        return CompletionSuggestResult("appreciation", options)
    }

    override fun backendCompleteionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("appreciation",
            listOf("nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        val option = KeyOption("name", options.map { it.options }.flatten().distinct())
        return CompletionSuggestResult("appreciation", listOf(option))
    }

    override fun indexToEs(appreciation: Appreciation) {
        logger.debug("Index Appreciation[${appreciation.id}] to elasticsearch")
        esIndexService.indexToEs(appreciation, appreciationMapper, appreciationEsRepository)
    }

    override fun indexToEs(appreciation: List<Appreciation>) {
        esIndexService.indexToEs(
            appreciation.map(Appreciation::id), appreciationMapper, appreciationRepository, appreciationEsRepository
        )
    }

    override fun asyncIndexToEs(appreciation: List<Appreciation>): CompletableFuture<Unit> {
        return esIndexService.asyncIndexToEs(
            appreciation.map(Appreciation::id), appreciationMapper, appreciationRepository, appreciationEsRepository
        )
    }

    override fun softDelete(entity: Appreciation) {
        entity.softDelete()
        appreciationRepository.save(entity).also {
            eventPublisher.publishEvent(EntityEvent.softDeleted(it))
        }
    }

    override fun softDelete(id: UUID) {
        softDelete(findExistsOrThrow(id))
    }

    override fun afterPublishedBeDelete(target: Appreciation) {
        /*同时删除动态*/
        eventPublisher.publishEvent(DeleteTimeLineEvent(target.id))
        eventPublisher.publishEvent(EntityEvent.softDeleted(target))
    }

    fun SpamTextLabel.getFeedbackText(): String {
        return when (this) {
            SpamTextLabel.normal -> "正常文本"
            SpamTextLabel.spam -> "垃圾内容"
            SpamTextLabel.abuse -> "辱骂"
            SpamTextLabel.ad -> "广告"
            SpamTextLabel.politics -> "涉政"
            SpamTextLabel.terrorism -> "暴恐"
            SpamTextLabel.porn -> "色情"
            SpamTextLabel.flood -> "灌水"
            SpamTextLabel.contraband -> "违禁"
            SpamTextLabel.meaningless -> "无意义"
            SpamTextLabel.customized -> "自定义"
        }
    }
}

