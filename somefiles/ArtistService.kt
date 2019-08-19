package com.musicbible.service

import com.boostfield.extension.collections.isNotNullAndNotEmptyThen
import com.boostfield.extension.string.containAllChinese
import com.boostfield.extension.string.containAnyChinese
import com.boostfield.extension.string.isNotNullAndNotBlankThen
import com.boostfield.extension.string.removePunct
import com.boostfield.extension.string.toUUID
import com.boostfield.spring.extension.withSortings
import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.CompletionSuggestService
import com.boostfield.spring.service.KeyCount
import com.boostfield.spring.service.KeyOption
import com.boostfield.spring.service.SearchService
import com.boostfield.spring.service.TermsAggregationResult
import com.musicbible.aspect.Locked
import com.musicbible.es.ALIAS_IDX_ARTIST
import com.musicbible.es.model.EsArtist
import com.musicbible.es.repository.ArtistEsRepository
import com.musicbible.event.EntityEvent
import com.musicbible.extension.transform
import com.musicbible.mapper.artist.ArtistBackendListInput
import com.musicbible.mapper.artist.ArtistFilterInput
import com.musicbible.mapper.artist.ArtistMapper
import com.musicbible.mapper.artist.Key
import com.musicbible.mapper.artist.UpdateArtistInput
import com.musicbible.mapper.release.ReleaseDataAnalysisOutput
import com.musicbible.model.Artist
import com.musicbible.model.DocumentStatus
import com.musicbible.model.QArtist
import com.musicbible.model.User
import com.musicbible.repository.ArtistRepository
import com.musicbible.repository.WorkArtistRepository
import com.musicbible.repository.toPair
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MultiMatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.filter.Filter
import org.elasticsearch.search.aggregations.bucket.nested.Nested
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.query.FetchSourceFilterBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface ArtistService :
    DocumentService<Artist>, ArtistRepository, SearchService<EsArtist, UUID>, CompletionSuggestService, DataAnalysisService {
    override val modelName: String
        get() = "艺术家"

    override fun publish(id: UUID)
    override fun suppress(id: UUID)
    fun create(): Artist
    fun updatePeriod(id: UUID, periodId: UUID?)
    fun updateStyles(id: UUID, styleIds: List<UUID>)
    fun updateImages(id: UUID, images: Array<String>)
    fun updateGenres(id: UUID, genreIds: List<UUID>)
    fun updateNationality(id: UUID, countryId: UUID?)
    fun updateProfessions(id: UUID, professionIds: List<UUID>)
    fun updateFields(id: UUID, fields: UpdateArtistInput)
    fun backendListSearch(input: ArtistBackendListInput): Page<Artist>
    fun updateMembers(id: UUID, memberIds: List<UUID>)
    fun frontendListSearch(input: ArtistFilterInput): Page<Artist>
    fun aggregationList(input: ArtistFilterInput): List<TermsAggregationResult>
    fun aggregationOnProfession(id: UUID): TermsAggregationResult
    fun indexToEs(artist: Artist)
    fun indexToEs(artists: List<Artist>)
    fun asyncIndexToEs(artists: List<Artist>): CompletableFuture<Unit>
    fun freshCollectCount(artist: Artist, latestCount: Long)
    fun freshWorkCount(artist: Artist)
    fun freshReleaseCount(artist: Artist)
    fun pageByCreator(user: User, keyword: String?, pageable: Pageable): Page<Artist>
    fun dataAnalysis(): ReleaseDataAnalysisOutput
    fun similar(artist: Artist, pageable: Pageable): Page<EsArtist>
}

@Service
@Transactional
class ArtistServiceImpl(
    @Autowired @PersistenceContext val em: EntityManager,
    @Autowired val artistRepository: ArtistRepository,
    @Autowired val genreService: GenreService,
    @Autowired val styleService: StyleService,
    @Autowired val countryService: CountryService,
    @Autowired val periodService: PeriodService,
    @Autowired val professionService: ProfessionService,
    @Autowired val artistMapper: ArtistMapper,
    @Autowired val artistEsRepository: ArtistEsRepository,
    @Autowired val workArtistRepository: WorkArtistRepository,
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val esIndexService: EsIndexService<Artist, EsArtist>,
    @Autowired val professionTypeService: ProfessionTypeService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : ArtistService, ArtistRepository by artistRepository {

    /**
     * 统计目标时间段内的增长量。
     *
     * @param begin 开始统计时间
     * @param end   结束统计时间
     * @param linearType 分析类型
     * @param platform   分析平台
     * @sine 2019年8月6日, PM 03:23:18
     */
    override fun linearAnalysis(
        begin: ZonedDateTime,
        end: ZonedDateTime,
        linearType: LinearType,
        platform: PlatformType
    ): Map<String, Long> {
        return when (linearType) {
            LinearType.MONTH_LINEAR -> linearAnalysisByMonth(begin, end, platform)
            LinearType.YEAR_LINEAR -> linearAnalysisByYear(begin, end, platform)
        }
    }

    private fun linearAnalysisByYear(begin: ZonedDateTime, end: ZonedDateTime, platform: PlatformType): Map<String, Long> {
        return when (platform) {
            PlatformType.BACKEND -> backendYearProfit(begin, end).map(::toPair).toMap()
            PlatformType.FRONTEND -> throw NotImplementedError()
        }
    }

    private fun linearAnalysisByMonth(begin: ZonedDateTime, end: ZonedDateTime, platform: PlatformType): Map<String, Long> {
        return when (platform) {
            PlatformType.BACKEND -> backendMonthProfit(begin, end).map(::toPair).toMap()
            PlatformType.FRONTEND -> throw NotImplementedError()
        }
    }

    /**
     * 统计目标时间段内录入的有效数量。
     *
     * @param begin 开始统计的时间点
     * @param end 结束统计的时间点
     * @return 统计结果
     * @since 2019年8月5日, PM 04:07:09
     */
    override fun newlyEnteringCountBetween(begin: ZonedDateTime, end: ZonedDateTime): Long {
        return count(qArtist.published.isTrue
            .and(qArtist.createdAt.between(begin, end))
            .and(qArtist.deleted.isFalse))
    }

    /**
     * 统计库内所有的有效数据量。
     *
     * @return 统计结果
     * @since 2019年8月5日, PM 04:11:15
     */
    override fun total(): Long {
        return count(qArtist.published.isTrue
            .and(qArtist.deleted.isFalse))
    }


    private val logger = LoggerFactory.getLogger(this.javaClass)
    val qArtist: QArtist = QArtist.artist

    override fun create(): Artist {
        val artist = save(Artist())
        logger.info("Create Artist[{}]", artist.id)
        eventPublisher.publishEvent(EntityEvent.created(artist))
        return artist
    }

    @Locked("artist-%{#id}")
    override fun publish(id: UUID) {
        val artist = findDraftOrThrow(id)
        artist.mustBeDraft()
        artist.publish()
        val saved = save(artist)
        logger.info("Publish Artist[{}]", saved.id)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#id}")
    override fun suppress(id: UUID) {
        val artist = findPublishedOrThrow(id)
        artist.mustPublished()
        artist.suppress()
        val saved = save(artist)
        logger.info("Suppress Artist[{}]", saved.id)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#id}")
    override fun softDelete(id: UUID) {
        val artist = findUnarchivedOrThrow(id)
        artist.softDelete()
        val saved = artistRepository.save(artist)
        logger.info("Artist[{}] soft deleted", saved.id)
        eventPublisher.publishEvent(EntityEvent.softDeleted(saved))
    }

    @Locked("artist-%{#id}")
    override fun updateGenres(id: UUID, genreIds: List<UUID>) {
        val artist = findUnarchivedOrThrow(id)
        val genres = genreIds.map(genreService::findOrThrow)
        artist.genres.clear()
        artist.genres.addAll(genres.toMutableSet())
        eventPublisher.publishEvent(EntityEvent.updated(artist))
    }

    @Locked("artist-%{#id}")
    override fun updateImages(id: UUID, images: Array<String>) {
        val artist = findUnarchivedOrThrow(id)
        artist.images = images
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#id}")
    override fun updateStyles(id: UUID, styleIds: List<UUID>) {
        val artist = findUnarchivedOrThrow(id)
        val styles = styleIds.map(styleService::findOrThrow)
        artist.styles.clear()
        artist.styles.addAll(styles.toMutableSet())
        eventPublisher.publishEvent(EntityEvent.updated(artist))
    }

    @Locked("artist-%{#id}")
    override fun updatePeriod(id: UUID, periodId: UUID?) {
        val artist = findUnarchivedOrThrow(id)
        if (periodId == null) {
            artist.period = null
        } else {
            artist.period = periodService.findOrThrow(periodId)
        }
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#id}")
    override fun updateNationality(id: UUID, countryId: UUID?) {
        val artist = findUnarchivedOrThrow(id)
        if (countryId == null) {
            artist.nationality = null
        } else {
            artist.nationality = countryService.findOrThrow(countryId)
        }
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#id}")
    override fun updateProfessions(id: UUID, professionIds: List<UUID>) {
        val artist = findUnarchivedOrThrow(id)
        val professions = professionIds.map(professionService::findOrThrow)
        artist.professions.clear()
        artist.professions.addAll(professions.toMutableSet())
        eventPublisher.publishEvent(EntityEvent.updated(artist))
    }

    @Locked("artist-%{#id}")
    override fun updateFields(id: UUID, fields: UpdateArtistInput) {
        val artist = findUnarchivedOrThrow(id)
        fields.type?.also { artist.type = it }
        fields.firstName?.also { artist.firstName = it }
        fields.lastName?.also { artist.lastName = it }
        fields.firstName2?.also { artist.firstName2 = it }
        fields.lastName2?.also { artist.lastName2 = it }
        fields.nameCN?.also { artist.nameCN = it }
        fields.abbr?.also { artist.abbr = it }
        fields.abbrCN?.also { artist.abbrCN = it }
        fields.aliases?.also { artist.aliases = it }
        fields.relatedSites?.also { artist.relatedSites = it }
        fields.gender?.also { artist.gender = it }
        fields.birthTime?.also { artist.birthTime = it }
        fields.deathTime?.also { artist.deathTime = it }
        fields.intro?.also { artist.intro = it }
        fields.commendLevel?.also { artist.commendLevel = it }
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#id}")
    override fun updateMembers(id: UUID, memberIds: List<UUID>) {
        val artist = findUnarchivedOrThrow(id)
        val members = memberIds.map(::findPublishedOrThrow)
        artist.members.clear()
        artist.members.addAll(members.toMutableSet())
        eventPublisher.publishEvent(EntityEvent.updated(artist))
    }

    override fun backendListSearch(input: ArtistBackendListInput): Page<Artist> {
        val searchQuery = NativeSearchQueryBuilder()
        val queryBuilder = BoolQueryBuilder()
        val q = input.q.removePunct()
        if (!q.isNullOrEmpty()) {
            input.key?.also {
                when (it) {
                    Key.TITLE ->
                        queryBuilder.must(
                            if (q.containAnyChinese()) {
                                QueryBuilders.boolQuery()
                                    .should(
                                        QueryBuilders.multiMatchQuery(q, "nameCN", "abbrCN")
                                            .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                                    ).should(
                                        QueryBuilders.prefixQuery("nameCN", q)
                                            .boost(@Suppress("MagicNumber") 5.0f)
                                    ).should(
                                        QueryBuilders.prefixQuery("abbrCN", q)
                                            .boost(@Suppress("MagicNumber") 5.0f)
                                    )
                            } else {
                                QueryBuilders.multiMatchQuery(q, "firstName",
                                    "lastName", "abbr", "nameCN.py", "abbrCN.py").type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                            }
                        )
                    Key.CREATEDBY ->
                        queryBuilder.must(
                            QueryBuilders.matchQuery("createdBy.nickName", q)
                        )
                }
            }
        }

        input.professionId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("professionIds", it.toString()))
        }

        input.nationalityId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("nationalityId", it.toString()))
        }

        input.genreId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("genreIds", it.toString()))
        }

        input.periodId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("periodId", it.toString()))
        }

        if (input.status.isNotEmpty()) {
            queryBuilder.filter(QueryBuilders.termsQuery("status", input.status.map { it.name }))
        }

        searchQuery.withQuery(queryBuilder)
            .withPageable(input.pageable())
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
            .withSortings(input.sortings)
        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsArtist::class.java)
            .transform(this)
    }

    @Suppress("MagicNumber")
    private fun buildQueryBuilder(input: ArtistFilterInput): QueryBuilder {
        val queryBuilder = BoolQueryBuilder()
        val q = input.q.removePunct()
        if (!q.isNullOrEmpty()) {
            queryBuilder.must(
                when {
                    q.containAllChinese() -> QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.multiMatchQuery(q, "nameCN", "abbrCN")
                                .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                        ).should(
                            QueryBuilders.prefixQuery("nameCN", q)
                                .boost(5.0f)
                        ).should(
                            QueryBuilders.prefixQuery("abbrCN", q)
                                .boost(5.0f)
                        )
                    q.containAnyChinese() ->
                        MultiMatchQueryBuilder(q)
                            .field("nameCN", 3.0f)
                            .field("abbrCN", 5.0f)
                            .field("firstName", 1.0f)
                            .field("lastName", 1.0f)
                            .field("abbr", 1.0f)
                            .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                    else ->
                        MultiMatchQueryBuilder(q)
                            .field("nameCN", 1.0f)
                            .field("abbrCN", 1.0f)
                            .field("firstName", 2.0f)
                            .field("lastName", 3.0f)
                            .field("abbr", 3.0f)
                            .field("abbrCN.py", 3.0f)
                            .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                }
            )
        }
        //组合professionIds和professiontypeIds
        val professionIds = mutableListOf<UUID>()
        input.professionIds.isNotNullAndNotEmptyThen {
            professionIds.addAll(it)
        }
        input.professiontypeIds.isNotNullAndNotEmptyThen {
            val subProfessions = professionTypeService.findProfessionIds(it.toList())
                .map { it.toUUID() }
            professionIds.addAll(subProfessions)
        }
        if (professionIds.isEmpty().not()) {
            queryBuilder.filter(QueryBuilders.termsQuery("professionIds",
                professionIds.map { id -> id.toString() }
            ))
        }

        input.genreIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(QueryBuilders.termsQuery("genreIds",
                it.map { id -> id.toString() }
            ))
        }

        input.styleIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(QueryBuilders.termsQuery("styleIds",
                it.map { id -> id.toString() }
            ))
        }

        input.gender?.also {
            queryBuilder.filter(QueryBuilders.termQuery("gender", it.name))
        }

        input.periodId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("periodId", it.toString()))
        }

        input.nationalityId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("nationalityId", it.toString()))
        }

        input.type?.also {
            queryBuilder.filter(QueryBuilders.termQuery("type", it.name))
        }
        input.firstLetter.isNotNullAndNotBlankThen {
            queryBuilder.filter(QueryBuilders.termQuery("firstLetter", input.firstLetter!!.first().toString()))

        }
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        return queryBuilder
    }

    override fun frontendListSearch(input: ArtistFilterInput): Page<Artist> {
        return frontendListSearchTemplate(
            ALIAS_IDX_ARTIST,
            input,
            elasticsearchTemplate,
            EsArtist::class.java,
            "id") {
            it.withQuery(buildQueryBuilder(input))
            if(input.shouldForceSort()) {
                it.withSortings(input.sortings)
            } else {
                it.withSortings(listOf(SortBuilders.scoreSort(), SortBuilders.fieldSort("commendLevel").order(SortOrder.DESC)))
            }
            it
        }.transform(this)
    }

    override fun aggregationList(input: ArtistFilterInput): List<TermsAggregationResult> {
        return aggregationListTemplate("artist",
            listOf("genreIds", "styleIds", "gender", "type", "professionIds", "periodId", "nationalityId", "firstLetter"),
            elasticsearchTemplate) {
            buildQueryBuilder(input)
        }
    }

    override fun aggregationOnProfession(id: UUID): TermsAggregationResult {
        val searchQuery = NativeSearchQueryBuilder().withIndices("release")
        val creditAgg = AggregationBuilders.nested("nestedCredits", "credits")
        val filterArtist = AggregationBuilders.filter(
            "matchArtist", QueryBuilders.termQuery("credits.artistId", id.toString())
        )
        val professionAgg = AggregationBuilders
            .terms("professions")
            .field("credits.professionId")
            .size(@Suppress("MagicNumber") 1000)
        filterArtist.subAggregation(professionAgg)
        creditAgg.subAggregation(filterArtist)

        searchQuery.addAggregation(creditAgg)
        return elasticsearchTemplate.query(searchQuery.build()) {
            val innerCreditAgg = it.aggregations.get<Nested>("nestedCredits")
            val filterAgg = innerCreditAgg.aggregations.get<Filter>("matchArtist")
            val termsAgg = filterAgg.aggregations.get<Terms>("professions")
            val kcs = termsAgg.buckets.map { bucket -> KeyCount(bucket.key.toString(), bucket.docCount) }
                .filter { it.key != "0" }
            TermsAggregationResult("profession", kcs)
        }
    }

    override fun indexToEs(artist: Artist) {
        logger.debug("Index Artist[${artist.id}] to elasticsearch")
        esIndexService.indexToEs(artist, artistMapper, artistEsRepository)
    }

    override fun indexToEs(artists: List<Artist>) {
        esIndexService.indexToEs(
            artists.map(Artist::id), artistMapper, artistRepository, artistEsRepository
        )
    }

    override fun asyncIndexToEs(artists: List<Artist>): CompletableFuture<Unit> {
        return esIndexService.asyncIndexToEs(
            artists.map(Artist::id), artistMapper, artistRepository, artistEsRepository
        )
    }

    @Locked("artist-%{#artist.id}")
    override fun freshCollectCount(artist: Artist, latestCount: Long) {
        artist.collectCount = latestCount
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#artist.id}")
    override fun freshReleaseCount(artist: Artist) {
        artist.releaseCount = countArtistRelease(artist)
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("artist-%{#artist.id}")
    override fun freshWorkCount(artist: Artist) {
        artist.workCount = workArtistRepository.countByArtistAndWorkNotDeleted(artist)
        val saved = save(artist)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun pageByCreator(user: User, keyword: String?, pageable: Pageable): Page<Artist> {
        var eq = qArtist.createdBy.eq(user).and(qArtist.deleted.isFalse)
        keyword?.also { eq = eq.and(qArtist.nameCN.like("%$it%")) }
        return findAll(eq, pageable)
    }

    override fun dataAnalysis(): ReleaseDataAnalysisOutput {
        val nowTime: ZonedDateTime = ZonedDateTime.now()
        val yesterday = ZonedDateTime.now().plusDays(-1)
        val yesterdayDataDifference = countByDay(
            nowTime.year,
            nowTime.monthValue,
            nowTime.dayOfMonth
        ) - countByDay(yesterday.year, yesterday.monthValue, yesterday.dayOfMonth)
        val lastMonth = ZonedDateTime.now().plusMonths(-1)
        val monthDataDifference = countByMonth(nowTime.year, nowTime.monthValue) - countByMonth(lastMonth.year, lastMonth.monthValue)
        return ReleaseDataAnalysisOutput(count(), yesterdayDataDifference, monthDataDifference)
    }

    override fun completionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("artist",
            listOf("nameCNCompletion", "nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        return CompletionSuggestResult("artist", options)
    }

    override fun backendCompleteionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("artist",
            listOf("nameCNCompletion", "nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        val option = KeyOption("name", options.map { it.options }.flatten().distinct())
        return CompletionSuggestResult("artist", listOf(option))
    }

    override fun similar(artist: Artist, pageable: Pageable): Page<EsArtist> {
        val queryBuilder = BoolQueryBuilder()

        //过滤自身ID
        queryBuilder.mustNot(QueryBuilders.idsQuery().addIds(artist.stringId))

        queryBuilder.should(QueryBuilders.termQuery("type", artist.type.name))

        artist.periodId?.also {
            queryBuilder.should(QueryBuilders.termQuery("periodId", it.toString()))
        }

        artist.nationalityId?.also {
            queryBuilder.should(QueryBuilders.termQuery("nationalityId", it.toString()))
        }

        if (artist.professions.isNotEmpty()) {
            queryBuilder.should(
                QueryBuilders.termsQuery("professionIds", artist.professionIds.map(UUID::toString))
            )
        }

        if (artist.genres.isNotEmpty()) {
            queryBuilder.should(
                QueryBuilders.termsQuery("genreIds", artist.genreIds.map(UUID::toString))
            )
        }

        if (artist.styles.isNotEmpty()) {
            queryBuilder.should(
                QueryBuilders.termsQuery("styleIds", artist.styleIds.map(UUID::toString))
            )
        }
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
            .filter(QueryBuilders.termsQuery("id", artist.stringId))

        val searchQuery = NativeSearchQueryBuilder()
        searchQuery.withQuery(queryBuilder)
            .withPageable(pageable)

        return artistEsRepository.search(searchQuery.build())
    }
}
