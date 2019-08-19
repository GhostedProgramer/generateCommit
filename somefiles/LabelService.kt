package com.musicbible.service

import com.boostfield.extension.string.containAnyChinese
import com.boostfield.extension.string.isNotNullAndNotBlankThen
import com.boostfield.extension.string.removePunct
import com.boostfield.spring.extension.withSortings
import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.CompletionSuggestService
import com.boostfield.spring.service.KeyOption
import com.boostfield.spring.service.SearchService
import com.boostfield.spring.service.TermsAggregationResult
import com.musicbible.aspect.Locked
import com.musicbible.es.ALIAS_IDX_LABEL
import com.musicbible.es.model.EsLabel
import com.musicbible.es.repository.LabelEsRepository
import com.musicbible.event.EntityEvent
import com.musicbible.extension.transform
import com.musicbible.mapper.label.BackendListInput
import com.musicbible.mapper.label.Key
import com.musicbible.mapper.label.LabelFilterInput
import com.musicbible.mapper.label.LabelMapper
import com.musicbible.mapper.label.UpdateLabelInput
import com.musicbible.model.DocumentStatus
import com.musicbible.model.Label
import com.musicbible.repository.CatalogRepository
import com.musicbible.repository.LabelRepository
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.query.FetchSourceFilterBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.CompletableFuture

interface LabelService :
    DocumentService<Label>, LabelRepository, SearchService<EsLabel, UUID>, CompletionSuggestService {
    override val modelName: String
        get() = "厂牌"

    fun create(): Label
    override fun publish(id: UUID)
    override fun suppress(id: UUID)
    fun updateParent(id: UUID, parentId: UUID?)
    fun updateImages(id: UUID, images: Array<String>)
    fun updateCountry(id: UUID, countryId: UUID?)
    fun updateFields(id: UUID, fields: UpdateLabelInput)
    fun backendListSearch(input: BackendListInput): Page<Label>
    fun frontendListSearch(input: LabelFilterInput): Page<Label>
    fun aggregationList(input: LabelFilterInput): List<TermsAggregationResult>
    fun indexToEs(label: Label)
    fun indexToEs(labels: List<Label>)
    fun asyncIndexToEs(labels: List<Label>): CompletableFuture<Unit>
    fun listChildren(id: UUID): List<Label>
    fun freshReleaseCount(label: Label)
}

@Service
@Transactional
class LabelServiceImpl(
    @Autowired val labelMapper: LabelMapper,
    @Autowired val labelEsRepository: LabelEsRepository,
    @Autowired val labelRepository: LabelRepository,
    @Autowired val countryService: CountryService,
    @Autowired val catalogRepository: CatalogRepository,
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val esIndexService: EsIndexService<Label, EsLabel>,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : LabelService, LabelRepository by labelRepository {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun create(): Label {
        val label = Label()
        return save(label)
    }

    @Locked("label-%{#id}")
    override fun publish(id: UUID) {
        val label = findDraftOrThrow(id)
        label.mustBeDraft()
        label.publish()
        val saved = save(label)
        logger.info("Publish Label[{}]", saved.id)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("label-%{#id}")
    override fun suppress(id: UUID) {
        val label = findPublishedOrThrow(id)
        label.mustPublished()
        label.suppress()
        val saved = save(label)
        logger.info("Suppress Label[{}]", saved.id)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("label-%{#id}")
    override fun softDelete(id: UUID) {
        val label = findUnarchivedOrThrow(id)
        label.softDelete()
        val saved = labelRepository.save(label)
        logger.info("Label[{}] soft deleted", saved.id)
        eventPublisher.publishEvent(EntityEvent.softDeleted(saved))
    }

    @Locked("label-%{#id}")
    override fun updateImages(id: UUID, images: Array<String>) {
        val label = findUnarchivedOrThrow(id)
        label.images = images
        val saved = save(label)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("label-%{#id}")
    override fun updateParent(id: UUID, parentId: UUID?) {
        val label = findUnarchivedOrThrow(id)
        val oldParent = label.parent
        if (parentId == null) {
            label.parent = null
            save(label)
        } else {
            val parent = findExistsOrThrow(parentId)
            label.parent = parent
            save(label)
            freshChildCount(parent)
        }

        eventPublisher.publishEvent(EntityEvent.updated(label))
        oldParent?.also {
            freshChildCount(it)
        }
    }

    @Locked("label-%{#id}")
    override fun updateCountry(id: UUID, countryId: UUID?) {
        val label = findUnarchivedOrThrow(id)
        if (countryId == null) {
            label.country = null
        } else {
            label.country = countryService.findOrThrow(countryId)
        }
        val saved = save(label)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("label-%{#id}")
    override fun updateFields(id: UUID, fields: UpdateLabelInput) {
        val label = findUnarchivedOrThrow(id)
        fields.name?.let { label.name = it }
        fields.nameCN?.let { label.nameCN = it }
        fields.registerTime?.let { label.registerTime = it }
        fields.relatedSites?.let { label.relatedSites = it }
        fields.intro?.let { label.intro = it }
        fields.commendLevel?.let { label.commendLevel = it }
        val saved = save(label)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun backendListSearch(input: BackendListInput): Page<Label> {
        val searchQuery = NativeSearchQueryBuilder()
        val queryBuilder = BoolQueryBuilder()
        val q = input.q.removePunct()
        if (!q.isNullOrEmpty()) {
            input.key?.also {
                when (it) {
                    Key.TITLE ->
                        queryBuilder.must(
                            if (q.containAnyChinese()) {
                                QueryBuilders.matchQuery("nameCN", input.q)
                            } else {
                                BoolQueryBuilder().should(
                                    QueryBuilders.wildcardQuery("name", "*${input.q!!.toLowerCase()}*")
                                        .boost(@Suppress("MagicNumber") 5f)

                                ).should(
                                    QueryBuilders.matchQuery("nameCN", input.q)
                                )
                            }
                        )
                    Key.CREATEDBY ->
                        queryBuilder.must(
                            QueryBuilders.matchQuery("createdBy.nickName", q)
                        )

                }
            }
        }

        input.countryId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("countryId", it.toString()))
        }

        if (input.status.isNotEmpty()) {
            queryBuilder.filter(QueryBuilders.termsQuery("status", input.status.map { it.name }))
        }

        searchQuery.withQuery(queryBuilder)
            .withPageable(input.pageable())
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
            .withSortings(input.sortings)

        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsLabel::class.java)
            .transform(this)
    }

    override fun frontendListSearch(input: LabelFilterInput): Page<Label> {
        return frontendListSearchTemplate(
            ALIAS_IDX_LABEL,
            input,
            elasticsearchTemplate,
            EsLabel::class.java,
            "id") {
            it.withQuery(buildQueryBuilder(input))
            if (input.shouldForceSort()) {
                it.withSortings(input.sortings)
            } else {
                it.withSortings(listOf(SortBuilders.scoreSort(), SortBuilders.fieldSort("commendLevel").order(SortOrder.DESC)))
            }
            it
        }.transform(this)
    }

    override fun aggregationList(input: LabelFilterInput): List<TermsAggregationResult> {
        return aggregationListTemplate(
            "label",
            listOf("countryId", "firstLetter", "registerYear"),
            elasticsearchTemplate
        ) {
            buildQueryBuilder(input)
        }
    }

    private fun buildQueryBuilder(input: LabelFilterInput): QueryBuilder {
        val queryBuilder = BoolQueryBuilder()
        val q = input.q.removePunct()
        if (!q.isNullOrEmpty()) {
            if (q.containAnyChinese()) {
                queryBuilder.must(QueryBuilders.matchQuery("nameCN", q))
            } else {
                queryBuilder
                    .must(
                        BoolQueryBuilder()
                            .should(QueryBuilders.matchQuery("nameCN", q))
                            .should(QueryBuilders.wildcardQuery("name", "*${q.toLowerCase()}*").boost(@Suppress("MagicNumber") 5f))
                    )
            }
        }
        input.countryId?.also {
            queryBuilder.filter(QueryBuilders.termsQuery("countryId", it.toString()))
        }

        input.firstLetter.isNotNullAndNotBlankThen {
            queryBuilder.filter(QueryBuilders.termQuery("firstLetter", it))
        }

        input.registerYear?.also {
            queryBuilder.filter(QueryBuilders.termQuery("registerYear", it))
        }

        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))

        return queryBuilder
    }

    override fun indexToEs(label: Label) {
        logger.debug("Index Label[${label.id}] to elasticsearch")
        esIndexService.indexToEs(label, labelMapper, labelEsRepository)
    }

    override fun indexToEs(labels: List<Label>) {
        esIndexService.indexToEs(
            labels.map(Label::id), labelMapper, labelRepository, labelEsRepository
        )
    }

    override fun asyncIndexToEs(labels: List<Label>): CompletableFuture<Unit> {
        return esIndexService.asyncIndexToEs(
            labels.map(Label::id), labelMapper, labelRepository, labelEsRepository
        )
    }

    private fun freshChildCount(label: Label) {
        label.childCount = countByDeletedFalseAndParent(label)
        val saved = save(label)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("label-%{#label.id}")
    override fun freshReleaseCount(label: Label) {
        label.releaseCount = catalogRepository.countByLabelAndReleaseNotDeleted(label)

        val saved = save(label)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun listChildren(id: UUID): List<Label> {
        return findExistsOrThrow(id).children
    }

    override fun completionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("label",
            listOf("nameCNCompletion", "nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        return CompletionSuggestResult("label", options)
    }

    override fun backendCompleteionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("label",
            listOf("nameCNCompletion", "nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)

        val option = KeyOption("name", options = options.map { it.options }.flatten().distinct())
        return CompletionSuggestResult("label", listOf(option))
    }
}
