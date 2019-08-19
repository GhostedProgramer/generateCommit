package com.musicbible.service

import com.boostfield.baidu.imagesearch.SearchResult
import com.boostfield.extension.collections.isNotNullAndNotEmptyThen
import com.boostfield.extension.collections.segement
import com.boostfield.extension.string.containAllChinese
import com.boostfield.extension.string.containAnyChinese
import com.boostfield.extension.string.containAnyNumber
import com.boostfield.extension.string.isNotNullAndNotBlankThen
import com.boostfield.extension.string.isNumber
import com.boostfield.extension.string.removePunct
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.extension.withSortings
import com.boostfield.spring.payload.PageQuery
import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.CompletionSuggestService
import com.boostfield.spring.service.KeyCount
import com.boostfield.spring.service.KeyOption
import com.boostfield.spring.service.SearchService
import com.boostfield.spring.service.TermsAggregationResult
import com.boostfield.spring.service.termsAggregationExtractor
import com.boostfield.spring.util.Collection.patchCollection
import com.musicbible.aspect.Locked
import com.musicbible.constant.UUIDConst
import com.musicbible.es.ALIAS_IDX_RELEASE
import com.musicbible.es.model.CreditDoc
import com.musicbible.es.model.EsRelease
import com.musicbible.es.repository.ReleaseEsRepository
import com.musicbible.event.DeleteTimeLineEvent
import com.musicbible.event.EntityEvent
import com.musicbible.event.ReleaseEventType
import com.musicbible.event.ReleaseImageUpdatedEvent
import com.musicbible.event.ReleaseNotificationEvent
import com.musicbible.event.TimeLineEvent
import com.musicbible.event.TimingPublishReleaseEvent
import com.musicbible.exception.TrackGroupException
import com.musicbible.extension.transform
import com.musicbible.mapper.audio.AudioDetailOutput
import com.musicbible.mapper.recording.RecordingArtistMapper
import com.musicbible.mapper.recording.UpdateRecordingArtistInput
import com.musicbible.mapper.release.CreateAndCommitInput
import com.musicbible.mapper.release.CreateDraftInput
import com.musicbible.mapper.release.CreateTrackInput
import com.musicbible.mapper.release.EditDraftAndCommitInput
import com.musicbible.mapper.release.EditPublishedReleaseInput
import com.musicbible.mapper.release.FormattedTrackOutput
import com.musicbible.mapper.release.FormattedTrackRecordingOutput
import com.musicbible.mapper.release.FormattedTrackTrackOutput
import com.musicbible.mapper.release.IssueInput
import com.musicbible.mapper.release.Key
import com.musicbible.mapper.release.MovementInTrack
import com.musicbible.mapper.release.ReleaseBackendListInput
import com.musicbible.mapper.release.ReleaseDataAnalysisOutput
import com.musicbible.mapper.release.ReleaseFilterInput
import com.musicbible.mapper.release.ReleaseMapper
import com.musicbible.mapper.release.ReleaseTimingInput
import com.musicbible.mapper.release.UpdateCatalogNumber
import com.musicbible.mapper.release.UpdateMediaInput
import com.musicbible.mapper.release.UpdateRecordingsArtistInput
import com.musicbible.mapper.release.UpdateReleaseArtistInput
import com.musicbible.mapper.release.UpdateReleaseInput
import com.musicbible.mapper.release.UpdateTrackInput
import com.musicbible.mapper.trackGroup.TrackGroupMapper
import com.musicbible.mapper.work.WorkArtistMapper
import com.musicbible.model.Catalog
import com.musicbible.model.DocumentStatus
import com.musicbible.model.DocumentSubStatus
import com.musicbible.model.Issue
import com.musicbible.model.ModelEnum
import com.musicbible.model.Movement
import com.musicbible.model.QRelease
import com.musicbible.model.Recording
import com.musicbible.model.RecordingArtist
import com.musicbible.model.Release
import com.musicbible.model.ReleaseArtist
import com.musicbible.model.SourceType
import com.musicbible.model.Track
import com.musicbible.model.TrackGroup
import com.musicbible.model.User
import com.musicbible.model.Work
import com.musicbible.model.WorkArtist
import com.musicbible.repository.ArtistRepository
import com.musicbible.repository.AudioRepository
import com.musicbible.repository.CatalogRepository
import com.musicbible.repository.IssueService
import com.musicbible.repository.RecordingDTO1
import com.musicbible.repository.ReleaseArtistRepository
import com.musicbible.repository.ReleaseRepository
import com.musicbible.repository.WorkDTO1
import com.musicbible.repository.projection.ArtistProfession
import com.musicbible.repository.toPair
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MultiMatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.nested.Nested
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.query.FetchSourceFilterBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Integer.min
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface ReleaseService :
    DocumentService<Release>, ReleaseRepository, SearchService<EsRelease, UUID>, CompletionSuggestService, DataAnalysisService {
    override val modelName: String
        get() = "唱片"

    fun publish(release: Release)
    fun suppress(release: Release)
    fun updatePeriod(release: Release, periodId: UUID?)
    fun updateStyles(id: UUID, styleIds: List<UUID>)
    fun updateGenres(id: UUID, genreIds: List<UUID>)
    fun updateImages(release: Release, images: Array<String>)
    fun updatePDFs(release: Release, pdfs: Array<Release.PDF>)
    fun updateForm(release: Release, formId: UUID?)
    fun updateInstruments(id: UUID, instrumentIds: List<UUID>)
    fun updateCatalogs(id: UUID, input: List<UpdateCatalogNumber>)
    fun updateType(release: Release, typeId: UUID?)
    fun updateMedia(id: UUID, body: UpdateMediaInput)
    fun updateStatus(id: UUID, status: DocumentStatus): Release
    fun addTrackByWork(id: UUID, workId: UUID, body: CreateTrackInput): Track
    fun addTrackByMovements(id: UUID, movementIds: List<UUID>): List<Track>
    fun updateArtists(id: UUID, body: List<UpdateReleaseArtistInput>)
    fun updateFields(release: Release, fields: UpdateReleaseInput)
    fun deleteTracks(id: UUID, trackIds: List<UUID>)
    fun formattedTracks(id: UUID): List<FormattedTrackOutput>
    fun swapTrackOrder(id: UUID, xId: UUID, yId: UUID)
    /**
     * 交换两组（同一作品下的录音为一组）音轨在唱片中的位置
     * 参数为音轨在唱片中的 index
     */
    fun batchSwapTrackOrder(trackGroupId: UUID, source: List<Int>, dest: List<Int>)

    fun updateTrackInfo(id: UUID, trackId: UUID, body: UpdateTrackInput)

    /**
     * 批量修改录音的艺术家
     */
    fun updateRecordingsArtists(id: UUID, input: UpdateRecordingsArtistInput)

    /**
     * 批量为录音新增艺术家
     */
    fun addRecordingsArtist(id: UUID, input: UpdateRecordingArtistInput)

    fun backendListSearch(input: ReleaseBackendListInput): Page<Release>
    fun frontendListSearch(input: ReleaseFilterInput): Page<Release>
    fun aggregationList(input: ReleaseFilterInput): List<TermsAggregationResult>
    fun indexToEs(release: Release)
    fun asyncIndexToEs(releases: List<Release>): CompletableFuture<Unit>
    fun freshCollectCount(release: Release, latestCount: Long)
    fun pageByCreator(user: User, keyword: String?, pageable: Pageable): Page<Release>
    fun dataAnalysis(): ReleaseDataAnalysisOutput
    fun onSaleReleaseDataAnalysis(): ReleaseDataAnalysisOutput
    fun freshSaleCount(release: Release, saleService: SaleService)
    fun similar(release: Release, pageable: Pageable): Page<Release>
    fun afterDelete(targetId: UUID)

    /**
     * 用户保存一个唱片草稿
     */
    fun save2Draft(body: CreateDraftInput): Release

    /**
     * 用户提交并发布一个唱片
     */
    fun saveAndCommit(body: CreateAndCommitInput): Release

    /**
     * 用户编辑草稿唱片
     */
    fun editDraft(release: Release, body: EditDraftAndCommitInput): Release

    /**
     * 用户发布一个草稿唱片
     */
    fun publishDraft(release: Release)

    /**
     * 用户编辑已发布的唱片
     */
    fun editPublishedRelease(release: Release, body: EditPublishedReleaseInput)

    /**
     * 用户个人的唱片草稿分页查询
     */
    fun drafts(user: User, q: String?, page: Pageable): Page<Release>

    /**
     * 個人中心唱片列表
     */
    fun personalList(user: User, q: String?, input: PageQuery): Page<Release>

    /**
     * 他人中心唱片列表
     */
    fun otherList(user: User, input: PageQuery): Page<Release>

    /**
     * 删除用户发布的唱片，并通知
     */
    fun deleteAndNotify(id: UUID, reason: String)

    /**
     * 撤销用户发布的唱片，并通知
     */
    fun suppressAndNotify(id: UUID)

    /**
     * 获取所有相关艺术家，包括作品和录音的
     */
    fun allCredits(release: Release): Set<ArtistProfession>

    /************************************************多主版本相关↓↓↓****************************************************/

    /**
     * 复制基本信息（不包括track和trackGroup）
     */
    fun copy(originId: UUID): Release

    fun copyAsSameEdition(originId: UUID): Release

    fun copyForEdit(id: UUID): Release

    /**
     * 将一个唱片关联到多个或一个主版本
     */
    fun relationToMaster(subjectId: UUID, masterIds: List<UUID>, force: Boolean = false): Release

    /**
     * 将多个唱片关联到一个主版本
     */
    fun relationToMaster(subjectIds: List<UUID>, masterId: UUID, force: Boolean = false): List<Release>

    /**
     * 子版本与主版本解除关联
     */
    fun clearRelationToMaster(subjectId: UUID, masterIds: List<UUID>): Release

    /**
     * 同版本同步修改
     *
     * <p>将主版本的音轨信息同步到同版本唱片中。
     * 并且，在同步之后，所有相关唱片会调用 {@code com.musicbible.service.ReleaseService.syncReleaseArtistFromWorkAndRecording}
     * 来同步艺术家列表。
     *
     * @return 返回被同步过的唱片
     */
    fun syncSubjectAndSyncReleaseArtist(masterId: UUID): List<Release>

    fun sameEditions(id: UUID, status: List<DocumentStatus>): MutableList<Release>

    /**
     * 只关联了一个主版本的唱片才能设置为主版本
     */
    fun setAsMaster(id: UUID)

    fun updateTrackGroupOrder(id: UUID, trackGroupId: UUID, move: TrackGroup.Move)

    fun refreshEditionCount(id: UUID)

    fun refreshEditionCount(sameEditionIds: List<UUID>)

    fun getMasters(id: UUID): MutableList<Release>

    /************************************************多主版本相关↑↑↑*****************************************************/

    /**
     * 通过图片搜索唱片
     */
    fun searchByImage(image: ByteArray): List<Pair<SearchResult, Release>>

    fun publish(user: User, release: Release)

    fun suppress(user: User, release: Release)

    fun suppressAndNotify(user: User, id: UUID)

    fun softDelete(user: User, release: Release)

    fun deleteAndNotify(user: User, id: UUID, reason: String)

    fun updateGenres(user: User, id: UUID, genreIds: List<UUID>)

    fun updateStyles(user: User, id: UUID, styleIds: List<UUID>)

    fun updateImages(user: User, release: Release, images: Array<String>)

    fun updatePDFs(user: User, release: Release, pdfs: Array<Release.PDF>)

    fun updatePeriod(user: User, release: Release, periodId: UUID?)

    fun updateForm(user: User, release: Release, formId: UUID?)

    fun updateInstruments(user: User, id: UUID, instrumentIds: List<UUID>)

    fun updateCatalogs(user: User, id: UUID, body: List<UpdateCatalogNumber>)

    fun updateType(user: User, release: Release, typeId: UUID?)

    fun updateMedia(user: User, id: UUID, body: UpdateMediaInput)

    fun updateRecordingsArtists(user: User, id: UUID, body: UpdateRecordingsArtistInput)

    fun swapTrackOrder(user: User, id: UUID, xId: UUID, yId: UUID)

    fun batchSwapTrackOrder(user: User, id: UUID, source: List<Int>, dest: List<Int>)

    fun updateTrackInfo(user: User, id: UUID, trackId: UUID, body: UpdateTrackInput)

    fun updateArtists(user: User, id: UUID, body: List<UpdateReleaseArtistInput>)

    fun updateFields(user: User, release: Release, body: UpdateReleaseInput)

    fun setAsMaster(user: User, id: UUID)

    fun relationToMaster(user: User, id: UUID, masterIds: List<UUID>, force: Boolean)

    fun clearRelationToMaster(user: User, subjectId: UUID, masterIds: List<UUID>): Release

    fun fetchSubject(user: User, id: UUID): List<Release>

    fun updateTrackGroupOrder(user: User, id: UUID, trackGroupId: UUID, move: TrackGroup.Move)

    fun editPublishedRelease(user: User, release: Release, body: EditPublishedReleaseInput)

    fun publishDraft(user: User, release: Release)

    fun editDraft(user: User, release: Release, body: EditDraftAndCommitInput)

    /**
     * 从唱片关联的录音和作品获取关联的艺术家组，
     * 并将其去重后，保存到ReleaseArtist。
     *
     * @param id 目标唱片ID
     * @since 2019年8月8日, PM 01:55:21
     */
    fun syncReleaseArtistFromWorkAndRecording(id: UUID)

    /**
     * 返回该唱片关联的作品关联的艺术家列表，和该唱片关联的录音关联的艺术家列表。
     * 需要过滤被软删的录音和作品。
     *
     * @since 2019年8月12日, AM 11:11:07
     */
    fun findWorkArtistAndRecordingArtist(release: Release): Pair<List<WorkArtist>, List<RecordingArtist>>

    /**
     * 获取主版本唱片关联的所有子版本唱片。
     *
     * @param id 主版本唱片ID。
     * @param q 搜索关键字，根据编号和唱片名称搜索，可为空
     * @param pageQuery 分页条件
     * @return 子版本列表，可为空。
     * @since 2019年8月5日, AM 11:07:46
     */
    fun getSubjectsOfTheMaster(id: UUID, q: String?, pageQuery: PageQuery): Page<Release>

    fun transToVest(id: UUID, user: User, input: ReleaseTimingInput)

}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class ReleaseServiceImpl(
    @Autowired @PersistenceContext val em: EntityManager,
    @Autowired val catalogRepository: CatalogRepository,
    @Autowired val releaseRepository: ReleaseRepository,
    @Autowired val releaseMapper: ReleaseMapper,
    @Autowired val releaseEsRepository: ReleaseEsRepository,
    @Autowired val releaseArtistRepository: ReleaseArtistRepository,
    @Autowired val releaseArtistService: ReleaseArtistService,
    @Autowired val genreService: GenreService,
    @Autowired val periodService: PeriodService,
    @Autowired val formService: FormService,
    @Autowired val releaseTypeService: ReleaseTypeService,
    @Autowired val instrumentService: InstrumentService,
    @Autowired val catalogService: CatalogService,
    @Autowired val labelService: LabelService,
    @Lazy @Autowired val recordingService: RecordingService,
    @Lazy @Autowired val trackService: TrackService,
    @Lazy @Autowired val trackGroupService: TrackGroupService,
    @Lazy @Autowired val workService: WorkService,
    @Autowired val artistRepository: ArtistRepository,
    @Autowired val mediaService: MediaService,
    @Autowired val mediaFeatureService: MediaFeatureService,
    @Autowired val styleService: StyleService,
    @Autowired val countryService: CountryService,
    @Autowired val movementService: MovementService,
    @Autowired val recordingArtistMapper: RecordingArtistMapper,
    @Autowired val workArtistMapper: WorkArtistMapper,
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val trackGroupMapper: TrackGroupMapper,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired @Lazy val billboardService: BillboardService,
    @Autowired @Lazy val userService: UserService,
    @Autowired val imageSearchService: ImageSearchService,
    @Autowired val audioRepository: AudioRepository,
    @Autowired val issueService: IssueService,
    @Autowired val quartzService: QuartzService
) : ReleaseService, ReleaseRepository by releaseRepository {

    /**
     * 从唱片关联的录音和作品获取关联的艺术家组，然后与Release建立关联。
     *
     * <p>需要注意被软删的作品/录音/乐章, 需要过滤。
     *
     * <p>需要在任何修改音轨信息的地方调用该方法。
     * 也就是修改RecordingArtist或者WorkArtist的时候，需要同步该
     * Work或者Recording间接关联的Release的艺术家列表。
     *
     *
     * 依据：
     * >> Release n-n Track n-1 Recording 1-n RecordingArtist
     * >> Release n-n Track n-1 Recording n-1 Work 1-n WorkArtist
     * >> Release n-n Track n-1 Recording n-1 Movement 1-n Work 1-n WorkArtist
     *
     * 覆盖点：
     * - 增删改RecordingArtist的时候。
     * - 主版本同步音轨到子版本。
     * - 录音修改关联艺术家的时候。
     * - 增删改WorkArtist的时候
     * - 作品修改关联艺术家或修改乐章的时候
     * - 增删改音轨
     * - 删除作品的时候
     * - 作品修改关联乐章的时候
     * - 增删改TrackGroup的时候
     * - ReleaseService中操作音轨的接口
     * - 有待增加，少一点就是BUG，自己看着办...
     *
     * 1. 增删改RecordingArtist的时候:
     *  - com.musicbible.service.RecordingArtistServiceImpl.remove (底层不覆盖)
     *  - com.musicbible.service.RecordingArtistServiceImpl.create (底层不覆盖)
     *  - com.musicbible.service.RecordingArtistServiceImpl.copy (底层不覆盖)
     *
     * 2. 主版本同步音轨到子版本：
     * - com.musicbible.service.ReleaseService.syncSubjectAndSyncReleaseArtist (已覆盖)
     *
     * 3. 录音修改关联艺术家的时候：
     * - com.musicbible.service.RecordingServiceImpl.addArtistAndSyncReleaseArtist (已覆盖)
     * - com.musicbible.service.RecordingServiceImpl.softDelete (已覆盖)
     * - com.musicbible.service.RecordingServiceImpl.updateArtistsAndSyncReleaseArtist (已覆盖)
     *
     * 4. 增删改WorkArtist的时候：
     * - com.musicbible.service.WorkArtistServiceImpl.create (底层不覆盖)
     * - com.musicbible.service.WorkArtistServiceImpl.remove (底层不覆盖)
     *
     * 5. 作品修改关联艺术家或修改乐章的时候：
     * - com.musicbible.service.WorkServiceImpl.updateArtistsAndSyncReleaseArtist (已覆盖)
     * - com.musicbible.service.WorkServiceImpl.dissociateRelease (已覆盖)
     *
     * 6. 增删改音轨:
     * - com.musicbible.service.TrackServiceImpl.deleteAllByRelease (已覆盖)
     * - com.musicbible.service.TrackServiceImpl.copyAndPersist (引用处已覆盖)
     *
     * 7. 删除作品的时候
     * - com.musicbible.service.WorkServiceImpl.softDelete
     *
     * 8. 作品修改关联乐章的时候：
     * - com.musicbible.service.WorkServiceImpl.dissociateMovementRelease (已覆盖)
     * - com.musicbible.service.WorkServiceImpl.deleteMovement (已覆盖)
     * - com.musicbible.service.WorkServiceImpl.appendMovement (新增乐章无需覆盖)
     * - com.musicbible.service.WorkServiceImpl.batchUpdateMovements (已覆盖)
     *
     * 9. 增删改TrackGroup的时候：
     * - com.musicbible.service.TrackGroupServiceImpl.copy (不覆盖， 在引用处覆盖)
     * - com.musicbible.service.TrackGroupServiceImpl.clearRelation (引用处已覆盖)
     *
     * 10. ReleaseService中操作音轨的接口：
     * - com.musicbible.service.ReleaseServiceImpl.addTrack (已覆盖)
     * - com.musicbible.service.ReleaseServiceImpl.deleteTracks (已覆盖)
     * - com.musicbible.service.ReleaseServiceImpl.copy(com.musicbible.model.Release) (已覆盖)
     * - com.musicbible.service.ReleaseServiceImpl.relationToMaster(com.musicbible.model.Release, java.util.List<? extends com.musicbible.model.Release>, boolean) (已覆盖)
     * - com.musicbible.service.ReleaseServiceImpl.relationToMaster(java.util.List<? extends com.musicbible.model.Release>, com.musicbible.model.Release, boolean) (已覆盖)
     * - com.musicbible.service.ReleaseServiceImpl.clearRelationToMaster(java.util.UUID, java.util.List<java.util.UUID>) (已覆盖)
     * - com.musicbible.service.ReleaseServiceImpl.syncSubjectAndSyncReleaseArtist (已覆盖)
     *
     * @param id 目标唱片ID
     * @author ywb
     * @since 2019年8月8日, PM 01:55:21
     */
    override fun syncReleaseArtistFromWorkAndRecording(id: UUID) {
        var release = findExistsOrThrow(id)
        val (workArtist, recordingArtists) = findWorkArtistAndRecordingArtist(release)

        // 处理旧数据
        val oldReleaseArtistList = release.credits.filter { it.sourceType == SourceType.NORMAL }
        if (oldReleaseArtistList.isNotEmpty()) {
            release.credits.removeAll(oldReleaseArtistList)
            oldReleaseArtistList.forEach(releaseArtistService::remove)
        }

        release = findExistsOrThrow(id)
        // 处理作品艺术家
        val releaseArtistsFromWork = release.credits.filter { it.sourceType == SourceType.SYNC_FROM_WORK }
        if (releaseArtistsFromWork.isNotEmpty()) {
            if (workArtist.isNotEmpty()) {
                val outReleaseArtistFromWork = releaseArtistsFromWork.filter { releaseArtist ->
                    workArtist.find { workArtist ->
                        workArtist.artistId == releaseArtist.artistId && workArtist.professionId == releaseArtist.professionId
                    } == null
                }
                if (outReleaseArtistFromWork.isNotEmpty()) {
                    release.credits.removeAll(outReleaseArtistFromWork)
                    outReleaseArtistFromWork.forEach(releaseArtistService::remove)
                }
                val inWorkArtist = workArtist.filter { workArtistObject ->
                    releaseArtistsFromWork.find { releaseArtist ->
                        releaseArtist.artistId == workArtistObject.artistId && releaseArtist.professionId == workArtistObject.professionId
                    } == null
                }
                if (inWorkArtist.isNotEmpty()) {
                    inWorkArtist.forEach { releaseArtistService.createFromWorkArtist(release, it) }
                }
            } else {
                // 清空
                release.credits.removeAll(releaseArtistsFromWork)
                releaseArtistsFromWork.forEach(releaseArtistService::remove)
            }
        } else {
            val newlyArtistFromWork = workArtist.distinctBy(WorkArtist::simpleHashCode)
            newlyArtistFromWork.forEach { releaseArtistService.createFromWorkArtist(release, it) }
        }

        release = findExistsOrThrow(id)
        // 处理录音艺术家
        val releaseArtistsFromRecording = release.credits.filter { it.sourceType == SourceType.SYNC_FROM_RECORDING }
        if (releaseArtistsFromRecording.isNotEmpty()) {
            if (recordingArtists.isNotEmpty()) {
                val outReleaseArtistsFromRecording = releaseArtistsFromRecording.filter { releaseArtist ->
                    recordingArtists.find { recordingArtist ->
                        recordingArtist.professionId == releaseArtist.professionId && recordingArtist.artistId == releaseArtist.artistId
                    } == null
                }
                if (outReleaseArtistsFromRecording.isNotEmpty()) {
                    release.credits.removeAll(outReleaseArtistsFromRecording)
                    outReleaseArtistsFromRecording.forEach(releaseArtistService::remove)
                }
                val inRecordingArtists = recordingArtists.filter { recordingArtist ->
                    releaseArtistsFromRecording.find { releaseArtist ->
                        releaseArtist.professionId == recordingArtist.professionId && releaseArtist.artistId == recordingArtist.artistId
                    } == null
                }
                if (inRecordingArtists.isNotEmpty()) {
                    releaseArtistService.createFromRecordingArtist(release, inRecordingArtists)
                }
                val crossRecordingArtist = recordingArtists - inRecordingArtists
                val crossReleaseArtists = releaseArtistsFromRecording - outReleaseArtistsFromRecording
                for (releaseArtist in crossReleaseArtists) {
                    val filter = crossRecordingArtist.filter {
                        it.professionId == releaseArtist.professionId && it.artistId == releaseArtist.artistId
                    }
                    val idStrings = filter.map(RecordingArtist::id).map(UUID::toString)
                    for (idString in idStrings) {
                        if (!releaseArtist.sources.contains(idString)) {
                            releaseArtist.sources = releaseArtist.sources.plus(idString)
                        }
                    }
                    releaseArtistService.save(releaseArtist)
                }
            } else {
                release.credits.removeAll(releaseArtistsFromRecording)
                releaseArtistsFromRecording.forEach(releaseArtistService::remove)
            }
        } else {
            releaseArtistService.createFromRecordingArtist(release, recordingArtists)
        }

        eventPublisher.publishEvent(EntityEvent.updated(release))
    }

    /**
     * 返回该唱片关联的作品关联的艺术家列表，和该唱片关联的录音关联的艺术家列表。
     * 需要过滤被软删的录音和作品。
     *
     * @since 2019年8月12日, AM 11:11:07
     */
    override fun findWorkArtistAndRecordingArtist(release: Release): Pair<List<WorkArtist>, List<RecordingArtist>> {
        val recordings = release.associatedExistRecordings
        val workArtist = recordings.mapNotNull(Recording::associatedExistWork).map(Work::credits).flatten()
        val workArtistWithMovement = recordings.mapNotNull(Recording::associatedExistMovement)
            .mapNotNull(Movement::associatedExistWork).map(Work::credits).flatten()

        val allWorkArtist = (workArtist + workArtistWithMovement).distinctBy { it.simpleHashCode() }
        val recordingArtists = recordings.map(Recording::credits).flatten().distinct()
        return allWorkArtist to recordingArtists

    }

    /**
     * 获取主版本唱片关联的所有子版本唱片。但是排除子版本。
     *
     * @param id 主版本唱片ID。
     * @param q 搜索关键字，根据编号和唱片名称搜索，可为空
     * @param pageQuery 分页条件
     * @return 子版本列表，可为空。
     * @since 2019年8月5日, AM 11:07:46
     */
    override fun getSubjectsOfTheMaster(id: UUID, q: String?, pageQuery: PageQuery): Page<Release> {
        val master = findMasterRelease(id)
        var pre = qRelease.masters.contains(master)
        if (!q.isNullOrBlank()) {

//            val catalogList = catalogService.findByNumberStartingWith(q)
//            if (catalogList.isNotEmpty()) {
//                for (catalog in catalogList) {
//                    qCondition = qCondition.or(qRelease.catalogs.contains(catalog))
//                }
//            }
            pre = pre.and(qRelease.titleCN.contains(q)
                .or(qRelease.title2.contains(q))
                .or(qRelease.title1.contains(q)))
        }
        return findAll(pre, pageQuery.defaultSortByCreateAt())
    }

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
            PlatformType.BACKEND -> advanceYearProfit(begin, end, true).map(::toPair).toMap()
            PlatformType.FRONTEND -> advanceYearProfit(begin, end, false).map(::toPair).toMap()
        }
    }

    private fun linearAnalysisByMonth(begin: ZonedDateTime, end: ZonedDateTime, platform: PlatformType): Map<String, Long> {
        return when (platform) {
            PlatformType.BACKEND -> advanceMonthProfit(begin, end, true).map(::toPair).toMap()
            PlatformType.FRONTEND -> advanceMonthProfit(begin, end, false).map(::toPair).toMap()
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
        return count(qRelease.published.isTrue
            .and(qRelease.createdAt.between(begin, end))
            .and(qRelease.deleted.isFalse))
    }

    /**
     * 统计库内所有的有效数据量。
     *
     * @return 统计结果
     * @since 2019年8月5日, PM 04:11:15
     */
    override fun total(): Long {
        return count(qRelease.published.isTrue
            .and(qRelease.deleted.isFalse))
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun editDraft(user: User, release: Release, body: EditDraftAndCommitInput) {
        editDraft(release, body)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun publishDraft(user: User, release: Release) {
        publishDraft(release)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun editPublishedRelease(user: User, release: Release, body: EditPublishedReleaseInput) {
        editPublishedRelease(release, body)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun suppressAndNotify(user: User, id: UUID) {
        suppressAndNotify(id)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun softDelete(user: User, release: Release) {
        softDelete(release)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun deleteAndNotify(user: User, id: UUID, reason: String) {
        deleteAndNotify(id, reason)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateGenres(user: User, id: UUID, genreIds: List<UUID>) {
        updateGenres(id, genreIds)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateStyles(user: User, id: UUID, styleIds: List<UUID>) {
        updateStyles(id, styleIds)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun updateImages(user: User, release: Release, images: Array<String>) {
        updateImages(release, images)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun updatePDFs(user: User, release: Release, pdfs: Array<Release.PDF>) {
        updatePDFs(release, pdfs)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun updatePeriod(user: User, release: Release, periodId: UUID?) {
        updatePeriod(release, periodId)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun updateForm(user: User, release: Release, formId: UUID?) {
        updateForm(release, formId)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateInstruments(user: User, id: UUID, instrumentIds: List<UUID>) {
        updateInstruments(id, instrumentIds)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateCatalogs(user: User, id: UUID, body: List<UpdateCatalogNumber>) {
        updateCatalogs(id, body)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun updateType(user: User, release: Release, typeId: UUID?) {
        updateType(release, typeId)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateMedia(user: User, id: UUID, body: UpdateMediaInput) {
        updateMedia(id, body)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateRecordingsArtists(user: User, id: UUID, body: UpdateRecordingsArtistInput) {
        updateRecordingsArtists(id, body)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun swapTrackOrder(user: User, id: UUID, xId: UUID, yId: UUID) {
        swapTrackOrder(id, xId, yId)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun batchSwapTrackOrder(user: User, id: UUID, source: List<Int>, dest: List<Int>) {
        batchSwapTrackOrder(id, source, dest)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateTrackInfo(user: User, id: UUID, trackId: UUID, body: UpdateTrackInput) {
        updateTrackInfo(id, trackId, body)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateArtists(user: User, id: UUID, body: List<UpdateReleaseArtistInput>) {
        updateArtists(id, body)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun updateFields(user: User, release: Release, body: UpdateReleaseInput) {
        updateFields(release, body)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun setAsMaster(user: User, id: UUID) {
        setAsMaster(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun relationToMaster(user: User, id: UUID, masterIds: List<UUID>, force: Boolean) {
        relationToMaster(id, masterIds, force)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun clearRelationToMaster(user: User, subjectId: UUID, masterIds: List<UUID>): Release {
        return clearRelationToMaster(subjectId, masterIds)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun fetchSubject(user: User, id: UUID): List<Release> {
        return syncSubjectAndSyncReleaseArtist(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateTrackGroupOrder(user: User, id: UUID, trackGroupId: UUID, move: TrackGroup.Move) {
        updateTrackGroupOrder(id, trackGroupId, move)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun suppress(user: User, release: Release) {
        suppress(release)
    }

    @Locked("%{#user.id}-%{#release.id}")
    @Transactional
    override fun publish(user: User, release: Release) {
        publish(release)
    }

    @Locked("%{#user.id}-%{#id}")
    override fun transToVest(id: UUID, user: User, input: ReleaseTimingInput) {
        val release = findExistsOrThrow(id)
        if (!release.fromAdmin) {
            throw AppError.BadRequest.default(msg = "不能将前台用户发布的唱片发布到动态")
        }
        eventPublisher.publishEvent(TimingPublishReleaseEvent(release.id, input.expectSendTime))
    }

    val qRelease: QRelease = QRelease.release

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun deleteAndNotify(id: UUID, reason: String) {
        val release = findUnarchivedOrThrow(id)
        release.fromAdmin.also {
            if (it) {
                throw AppError.BadRequest.default(msg = "该接口只能用于删除用户发布的唱片")
            }
        }
        softDelete(release)
        /*同时删除动态*/
        eventPublisher.publishEvent(DeleteTimeLineEvent(release.id))

        val event = ReleaseNotificationEvent(
            source = release,
            targetId = release.id,
            targetType = ModelEnum.Release,
            eventType = ReleaseEventType.DELETE,
            extra = reason,
            createdBy = release.createdBy!!
        )
        eventPublisher.publishEvent(event)
    }

    override fun suppressAndNotify(id: UUID) {
        val release = findPublishedOrThrow(id)
        release.fromAdmin.also {
            if (it) {
                throw AppError.BadRequest.default(msg = "该接口只能用于删除用户发布的唱片")
            }
        }
        release.subStatus = DocumentSubStatus.DRAFT_ADMIN_SUPPRESS
        suppress(release)
        /*同时删除动态*/
        eventPublisher.publishEvent(DeleteTimeLineEvent(release.id))
        val event = ReleaseNotificationEvent(
            source = release,
            targetId = release.id,
            targetType = ModelEnum.Release,
            eventType = ReleaseEventType.SUPPRESS,
            extra = null,
            createdBy = release.createdBy!!
        )
        eventPublisher.publishEvent(event)
    }

    override fun otherList(user: User, input: PageQuery): Page<Release> {
        var expression = qRelease.createdBy.eq(user)
            .and(qRelease.deleted.isFalse)
            .and(qRelease.fromAdmin.isFalse)
            .and(qRelease.status.eq(DocumentStatus.PUBLISHED))
            .and(qRelease.published.isTrue)
        return findAll(expression, input.defaultSortByCreateAt())
    }


    override fun personalList(user: User, q: String?, input: PageQuery): Page<Release> {
        var expression = qRelease.createdBy.eq(user)
            .and(qRelease.deleted.isFalse)
            .and(qRelease.fromAdmin.isFalse)
            .and(qRelease.status.eq(DocumentStatus.PUBLISHED).and(qRelease.published.isTrue))
        q?.also {
            expression = expression.and(qRelease.title1.contains(q)
                .or(qRelease.title2.contains(it))
                .or(qRelease.titleCN.contains(it))
            )
        }
        return findAll(expression, input.defaultSortByCreateAt())
    }


    /**
     * #1002322 取消发布前台用户的内容逻辑优化
     *
     * <p>个人中心草稿箱需要过滤被后台撤销发布的草稿
     * 状态{@code DocumentSubStatus@DRAFT_ADMIN_SUPPRESS}
     *
     * @since 2019年7月25日, AM 11:33:32
     *
     * <p>过滤后台创建的唱片
     *
     * @since 2019年7月30日, PM 02:52:16
     */
    override fun drafts(user: User, q: String?, page: Pageable): Page<Release> {
        var expression = qRelease.deleted.isFalse
            .and(qRelease.status.eq(DocumentStatus.DRAFT))
            .and(qRelease.subStatus.ne(DocumentSubStatus.DRAFT_ADMIN_SUPPRESS)) //被后台下架的草稿，不对用户显示。
            .and(qRelease.published.isFalse)
            .and(qRelease.fromAdmin.isFalse)
            .and(qRelease.createdBy.eq(user))

        q?.also {
            expression = expression.and(qRelease.title1.contains(it)
                .or(qRelease.title2.contains(it))
                .or(qRelease.titleCN.contains(it)))
        }
        return findAll(expression, page)
    }

    override fun editPublishedRelease(release: Release, body: EditPublishedReleaseInput) {
        if (release.status != DocumentStatus.PUBLISHED) {
            throw AppError.BadRequest.default(msg = "只能编辑发布唱片")
        }

        // 校验必填项
        if (body.images.isEmpty()
//            || body.artists.isEmpty()
            || body.genreIds.isEmpty()
            || body.media == null
        ) {
            throw AppError.BadRequest.default(msg = "必填项不能为空")
        }
        release.title1 = body.title1.orEmpty()
        release.title2 = body.title2.orEmpty()
        release.titleCN = body.titleCN.orEmpty()
        release.duration = body.duration
        release.intro = body.intro.orEmpty()
        release.images = body.images
        release.pdfs = body.pdfs

        // 更新主版本字段
        val masterId = body.masterId
        if (masterId != null) {
            if (masterId != release.master?.id) {
                // 主版本变更
                val masterRelease = findMasterRelease(masterId)
                release.master = masterRelease
                relationToMaster(release, listOf(masterRelease))
            }
        } else {
            if (!release.beMaster) {
                release.master = release
            }
        }

        billboardService.rebuildReleaseAssociate(release, body.billboardIds)
        // 更新关联字段
        modifyCatalogs(release, body.catalogNumbers)
        modifyArtists(release, body.artists)
        //兼容旧api
        if (!(body.issueTime.isNullOrEmpty() && body.regionIds.isEmpty())) {
            body.issues.add(IssueInput(body.issueTime.orEmpty(), body.regionIds))
        }
        modifyIssue(release, body.issues)
        modifyPeriod(release, body.periodId)
        modifyForm(release, body.formId)
        modifyGenres(release, body.genreIds)
        modifyStyles(release, body.styleIds)
        modifyInstruments(release, body.instrumentIds)
        if (body.media != null) {
            modifyMedia(release, body.media!!)
        }
        modifyType(release, body.type)

        release.calcCompleteness()
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun publishDraft(release: Release) {
        // 校验草稿状态
        if (release.status != DocumentStatus.DRAFT) {
            throw AppError.BadRequest.default(msg = "只能发布草稿唱片")
        }

        // 校验必填项
        if (release.images.isEmpty()
//            || release.artists.isEmpty()
            || release.genreIds.isEmpty()
            || release.media == null
        ) {
            throw AppError.BadRequest.default(msg = "必填项不能为空")
        }

        var new = updateStatus(release, DocumentStatus.PUBLISHED)
        new.published = true
        new.subStatus = DocumentSubStatus.PUBLISHED_USER_FROM_DRAFT
        new = save(new)
        syncReleaseArtistFromWorkAndRecording(new.id)

        eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Release, new.id, new.createdBy!!))
    }

    override fun editDraft(release: Release, body: EditDraftAndCommitInput): Release {
        // 参数不能全空
        if (body.images.isEmpty()
            && body.titleCN.isNullOrBlank()
            && body.title1.isNullOrBlank()
            && body.title2.isNullOrBlank()
            && body.duration == null
            && body.issueTime.isNullOrBlank()
            && body.intro.isNullOrBlank()
            && body.catalogNumbers.isEmpty()
//            && body.artists.isEmpty()
            && body.regionIds.isEmpty()
            && body.periodId == null
            && body.formId == null
            && body.genreIds.isEmpty()
            && body.styleIds.isEmpty()
            && body.instrumentIds.isEmpty()
            && body.media == null
            && body.type == null
            && body.masterId == null
            && body.pdfs.isEmpty()
        ) {
            throw AppError.BadRequest.default(msg = "参数不能全为空")
        }

        // 更新基本字段
        release.title1 = body.title1.orEmpty()
        release.title2 = body.title2.orEmpty()
        release.titleCN = body.titleCN.orEmpty()
        release.duration = body.duration
        release.intro = body.intro.orEmpty()
        release.images = body.images
        release.pdfs = body.pdfs

        // 更新主版本字段
        val masterId = body.masterId
        if (masterId != null) {
            if (masterId != release.master?.id) {
                // 主版本变更
                val masterRelease = findMasterRelease(masterId)
                release.master = masterRelease
                relationToMaster(release, listOf(masterRelease))
            }
        } else {
            if (!release.beMaster) {
                release.master = release
            }
        }

        // 更新关联字段
        billboardService.rebuildReleaseAssociate(release, body.billboardIds)

        modifyCatalogs(release, body.catalogNumbers)
        modifyArtists(release, body.artists)
        //兼容旧api
        if (!(body.issueTime.isNullOrEmpty() && body.regionIds.isEmpty())) {
            body.issues.add(IssueInput(body.issueTime.orEmpty(), body.regionIds))
        }
        modifyIssue(release, body.issues)
        modifyPeriod(release, body.periodId)
        modifyForm(release, body.formId)
        modifyGenres(release, body.genreIds)
        modifyStyles(release, body.styleIds)
        modifyInstruments(release, body.instrumentIds)
        if (body.media != null) {
            modifyMedia(release, body.media!!)
        }
        modifyType(release, body.type)

        release.calcCompleteness()
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
        return saved
    }

    override fun saveAndCommit(body: CreateAndCommitInput): Release {
        val images = body.images
//        val artists = body.artists
        val catalogNumbers = body.catalogNumbers
        val genreIds = body.genreIds
        val media = body.media
        if (images.isEmpty()
//            || artists.isEmpty()
            || genreIds.isEmpty()
            || media == null
        ) {
            throw AppError.BadRequest.default(msg = "必填项不能为空")
        }

        // 基本信息
        val release = Release().also {
            it.title1 = body.title1.orEmpty()
            it.title2 = body.title2.orEmpty()
            it.titleCN = body.titleCN.orEmpty()
            it.duration = body.duration
            it.intro = body.intro.orEmpty()
            it.images = images
            it.status = DocumentStatus.PUBLISHED
            it.subStatus = DocumentSubStatus.PUBLISHED_USER_AHEAD
            it.pdfs = body.pdfs
            it.published = true
            it.fromAdmin = false
        }
        val saveRelease = releaseRepository.save(release)
        val masterId = body.masterId
        if (masterId != null) {
            val masterRelease = findMasterRelease(masterId)
            saveRelease.master = masterRelease
            relationToMaster(saveRelease, listOf(masterRelease))
        } else {
            saveRelease.master = saveRelease
        }
        //兼容旧api
        if (!(body.issueTime.isNullOrEmpty() && body.regionIds.isEmpty())) {
            body.issues.add(IssueInput(body.issueTime.orEmpty(), body.regionIds))
        }
        modifyIssue(saveRelease, body.issues)
        if (body.periodId != null) {
            modifyPeriod(saveRelease, body.periodId)
        }
        if (body.formId != null) {
            modifyForm(saveRelease, body.formId)
        }
        if (body.styleIds.isNotEmpty()) {
            modifyStyles(saveRelease, body.styleIds)
        }
        if (body.instrumentIds.isNotEmpty()) {
            modifyInstruments(saveRelease, body.instrumentIds)
        }
        if (body.type != null) {
            modifyType(saveRelease, body.type!!)
        }

        billboardService.rebuildReleaseAssociate(saveRelease, body.billboardIds)
        modifyCatalogs(saveRelease, catalogNumbers)
//        modifyArtists(saveRelease, artists)
        modifyGenres(saveRelease, genreIds)
        modifyMedia(saveRelease, media)

        saveRelease.calcCompleteness()
        syncReleaseArtistFromWorkAndRecording(saveRelease.id)

        eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Release, saveRelease.id, saveRelease.createdBy!!))
        indexToEs(saveRelease)
        return saveRelease
    }

    override fun save2Draft(body: CreateDraftInput): Release {
        // 参数不能全空
        if (body.images.isEmpty()
            && body.titleCN.isNullOrBlank()
            && body.title1.isNullOrBlank()
            && body.title2.isNullOrBlank()
            && body.duration == null
            && body.issueTime.isNullOrBlank()
            && body.intro.isNullOrBlank()
            && body.catalogNumbers.isEmpty()
//            && body.artists.isEmpty()
            && body.regionIds.isEmpty()
            && body.periodId == null
            && body.formId == null
            && body.genreIds.isEmpty()
            && body.styleIds.isEmpty()
            && body.instrumentIds.isEmpty()
            && body.media == null
            && body.type == null
            && body.masterId == null
            && body.pdfs.isEmpty()
        ) {
            throw AppError.BadRequest.default(msg = "参数不能全为空")
        }

        // 基本信息
        val draft = Release().also {
            it.title1 = body.title1.orEmpty()
            it.title2 = body.title2.orEmpty()
            it.titleCN = body.titleCN.orEmpty()
            it.duration = body.duration
            it.intro = body.intro.orEmpty()
            it.images = body.images
            it.status = DocumentStatus.DRAFT
            it.subStatus = DocumentSubStatus.DRAFT_USER_NEW
            it.published = false
            it.pdfs = body.pdfs
            it.fromAdmin = false
        }
        val saveDraft = releaseRepository.save(draft)
        val masterId = body.masterId
        if (masterId != null) {
            val masterRelease = findMasterRelease(masterId)
            saveDraft.master = masterRelease
            relationToMaster(saveDraft, listOf(masterRelease))
        } else {
            saveDraft.master = saveDraft
        }

        billboardService.rebuildReleaseAssociate(saveDraft, body.billboardIds)
        if (body.catalogNumbers.isNotEmpty()) {
            modifyCatalogs(saveDraft, body.catalogNumbers)
        }
//        if (body.artists.isNotEmpty()) {
//            modifyArtists(saveDraft, body.artists)
//        }
        //兼容旧api
        if (!(body.issueTime.isNullOrEmpty() && body.regionIds.isEmpty())) {
            body.issues.add(IssueInput(body.issueTime.orEmpty(), body.regionIds))
        }
        modifyIssue(saveDraft, body.issues)

        if (body.periodId != null) {
            modifyPeriod(saveDraft, body.periodId)
        }
        if (body.formId != null) {
            modifyForm(saveDraft, body.formId)
        }
        if (body.genreIds.isNotEmpty()) {
            modifyGenres(saveDraft, body.genreIds)
        }
        if (body.styleIds.isNotEmpty()) {
            modifyStyles(saveDraft, body.styleIds)
        }
        if (body.instrumentIds.isNotEmpty()) {
            modifyInstruments(saveDraft, body.instrumentIds)
        }
        if (body.media != null) {
            modifyMedia(saveDraft, body.media!!)
        }
        if (body.type != null) {
            modifyType(saveDraft, body.type!!)
        }

        saveDraft.calcCompleteness()
        indexToEs(saveDraft)
        return saveDraft
    }

    private fun findMasterRelease(masterId: UUID): Release {
        val master = findExistsOrThrow(masterId)
        if (!master.beMaster) {
            throw AppError.BadRequest.default(msg = "请输入正确的主版本唱片!")
        }
        return master
    }

    override fun publish(release: Release) {
        release.publish()
        val saved = save(release)
        logger.info("Publish Release[{}]", saved.id)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    override fun suppress(release: Release) {
        if (release.status == DocumentStatus.WAITING) {
            quartzService.removeJob("onTimingPublishRelease.${release.id}")
        }
        release.suppress()
        release.expectSendTime = null
        val saved = save(release)
        logger.info("Suppress Release[{}]", saved.id)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    override fun softDelete(entity: Release) {
        if (entity.beMaster && entity.subjects.isNotEmpty())
            throw AppError.BadRequest.illegalOperate(msg = "主版本不可直接删除")
        if (entity.status == DocumentStatus.WAITING) {
            quartzService.removeJob("onTimingPublishRelease.${entity.id}")
        }

        entity.softDelete()
        val saved = releaseRepository.save(entity)
        logger.info("Release[{}] deleted", entity.id)
        eventPublisher.publishEvent(EntityEvent.softDeleted(saved))
        val sameEditions = sameEditions(saved, listOf(DocumentStatus.DRAFT, DocumentStatus.PUBLISHED, DocumentStatus.CHECKING))
        refreshEditionCount(sameEditions.map(Release::id))
    }

    override fun updateGenres(id: UUID, genreIds: List<UUID>) {
        val release = findUnarchivedOrThrow(id)
        modifyGenres(release, genreIds)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyGenres(release: Release, genreIds: List<UUID>) {
        val genres = genreIds.toMutableSet().map(genreService::findOrThrow)
        release.genres.clear()
        release.genres.addAll(genres)
    }

    override fun updateImages(release: Release, images: Array<String>) {
        val distinctImages = images.distinct().toTypedArray()
        eventPublisher.publishEvent(
            ReleaseImageUpdatedEvent(release.id, release.images, distinctImages)
        )
        release.images = distinctImages
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun updatePDFs(release: Release, pdfs: Array<Release.PDF>) {
        modifyPDFs(release, pdfs)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyPDFs(release: Release, pdfs: Array<Release.PDF>) {
        release.pdfs = pdfs
    }

    override fun updateStyles(id: UUID, styleIds: List<UUID>) {
        val release = findUnarchivedOrThrow(id)
        modifyStyles(release, styleIds)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyStyles(release: Release, styleIds: List<UUID>) {
        val styles = styleIds.toMutableSet().map(styleService::findOrThrow)
        release.styles.clear()
        release.styles.addAll(styles)
    }


    override fun updatePeriod(release: Release, periodId: UUID?) {
        modifyPeriod(release, periodId)
        save(release)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyPeriod(release: Release, periodId: UUID?) {
        if (periodId == null) {
            release.period = null
        } else {
            release.period = periodService.findOrThrow(periodId)
        }
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    override fun updateForm(release: Release, formId: UUID?) {
        modifyForm(release, formId)
        save(release)
        eventPublisher.publishEvent(EntityEvent.updated(release))
    }

    private fun modifyForm(release: Release, formId: UUID?) {
        if (formId == null) {
            release.form = null
        } else {
            release.form = formService.findOrThrow(formId)
        }
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    override fun updateInstruments(id: UUID, instrumentIds: List<UUID>) {
        val release = findUnarchivedOrThrow(id)
        modifyInstruments(release, instrumentIds)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyInstruments(release: Release, instrumentIds: List<UUID>) {
        val instruments = instrumentIds.toMutableSet().map(instrumentService::findOrThrow)
        release.instruments.clear()
        release.instruments.addAll(instruments)
    }


    override fun updateCatalogs(id: UUID, input: List<UpdateCatalogNumber>) {
        val release = findUnarchivedOrThrow(id)
        modifyCatalogs(release, input)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyCatalogs(release: Release, input: List<UpdateCatalogNumber>) {
        val catalogs = input.map {
            Catalog(
                labelService.findPublishedOrThrow(it.labelId),
                release,
                it.prefix,
                it.number
            )
        }.toSet()

        patchCollection(
            release.catalogs, catalogs, catalogService::remove, catalogService::create
        )
        release.updatedAt = ZonedDateTime.now()
    }

    override fun updateType(release: Release, typeId: UUID?) {
        modifyType(release, typeId)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyType(release: Release, typeId: UUID?) {
        if (typeId == null) {
            release.type = null
        } else {
            release.type = releaseTypeService.findOrThrow(typeId)
        }
        save(release)
    }

    override fun updateMedia(id: UUID, body: UpdateMediaInput) {
        val release = findUnarchivedOrThrow(id)
        modifyMedia(release, body)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun updateStatus(id: UUID, status: DocumentStatus): Release {
        val release = findExistsOrThrow(id)
        return updateStatus(release, status)
    }

    private fun updateStatus(release: Release, status: DocumentStatus): Release {
        release.status = status
        val saved = save(release)
        refreshEditionCount(release.id)
        return saved
    }

    private fun modifyMedia(release: Release, body: UpdateMediaInput) {
        release.media = body.mediaId?.let(mediaService::findOrThrow)
        release.mediaCount = body.mediaCount
        release.mediaFeatures.clear()
        body.mediaFeatureIds.forEach {
            it?.also { id ->
                release.mediaFeatures.add(mediaFeatureService.findOrThrow(id))
            }
        }
        save(release)
    }

    private fun addTrack(
        release: Release, recording: Recording, body: CreateTrackInput?, andDo: (Track) -> Unit = {}
    ): Track {
        // 先查找release本身是否有一个master为null的trackGroup
        var trackGroup: TrackGroup? = null
        for (tg in release.trackGroups) {
            if (tg.master == null) {
                trackGroup = tg
                break
            }
        }
        // 如果没找到则新建一个trackGroup
        if (trackGroup == null) {
            trackGroup = trackGroupService.create(release)
            release.trackGroups.add(trackGroup)
        }
        // 通过某些信息比如recording新建一个track
        var track = Track(release, recording, trackGroup = trackGroup)

        body?.also {
            track.order = body.order ?: release.maxTrackOrder + 1
            track.side = body.side
        }
        andDo(track)

        track = trackService.save(track)
        trackGroup.subjects.forEach { it.fetched = false }
        trackGroupService.save(trackGroup)
        trackGroupService.saveAll(trackGroup.subjects)
        return track
    }

    override fun addTrackByWork(id: UUID, workId: UUID, body: CreateTrackInput): Track {
        val release = findUnarchivedOrThrow(id)
        val work = workService.findPublishedOrThrow(workId)

        var recording = Recording.fromWork(work)
        recording.release = release
        recording = recordingService.save(recording)

        val track = addTrack(release, recording, body)

        release.updatedAt = ZonedDateTime.now()
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))

        return track
    }

    override fun addTrackByMovements(id: UUID, movementIds: List<UUID>): List<Track> {
        val release = findUnarchivedOrThrow(id)
        val movements = movementIds.map(movementService::findOrThrow)

        var maxOrder = release.maxTrackOrder
        val tracks = movements.map {
            var recording = Recording.fromMovement(it)
            recording.release = release
            recording = recordingService.save(recording)
            addTrack(release, recording, null) { track -> track.order = ++maxOrder }
        }

        release.updatedAt = ZonedDateTime.now()
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))

        return tracks
    }


    override fun updateArtists(id: UUID, body: List<UpdateReleaseArtistInput>) {
        val release = findUnarchivedOrThrow(id)
        modifyArtists(release, body)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    private fun modifyArtists(release: Release, body: List<UpdateReleaseArtistInput>) {
        //Do nothing
//        val updated = body.map {
//            ReleaseArtist(
//                release,
//                artistRepository.findByDeletedFalseAndPublishedTrueAndId(it.artistId)
//                    ?: throw AppError.NotFound.default(msg = "找不到 id 为 ${it.artistId} 的艺术家"),
//                it.order, it.main
//            )
//        }
//        patchCollection(
//            release.credits, updated,
//            releaseArtistService::remove, releaseArtistService::create
//        ) { origin, news ->
//            origin.order = news[0].order
//            origin.main = news[0].main
//            releaseArtistService.save(origin)
//        }
//        release.updatedAt = ZonedDateTime.now()
    }

    override fun updateFields(release: Release, fields: UpdateReleaseInput) {
        fields.titleCN?.also { release.titleCN = it }
        fields.title1?.also { release.title1 = it }
        fields.title2?.also { release.title2 = it }
        fields.duration?.also { release.duration = it }
        fields.intro?.also { release.intro = it }
        fields.commendLevel?.also { release.commendLevel = it }
        modifyIssue(release, fields.issues)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun deleteTracks(id: UUID, trackIds: List<UUID>) {
        val release = findUnarchivedOrThrow(id)
        val tracks = trackService.findAllById(trackIds)
        val trackGroups = mutableSetOf<TrackGroup>()
        for (track in tracks) {
            // 设置同步状态
            val trackGroup = track.trackGroup
            if (release.beMaster) {
                trackGroup.subjects.forEach { it.fetched = false }
            } else {
                trackGroup.fetched = false
            }
            trackGroups.add(track.trackGroup)
            // 软删除recording
            recordingService.softDelete(track.recording)
        }
        trackService.deleteAllByIdIn(trackIds)
        trackGroupService.saveAll(trackGroups)

        syncReleaseArtistFromWorkAndRecording(id)
    }

    override fun formattedTracks(id: UUID): List<FormattedTrackOutput> {
        val release = findUnarchivedOrThrow(id)
        val formattedTrackTrackOutputsList = MutableList(release.trackGroups.size) {
            if (release.trackGroups[it].tracks.isEmpty())
                emptyList()
            else
                formattedTracksByTrackGroup(release.trackGroups[it])
        }
        return formattedTrackTrackOutputsList
            .flatten()
    }

    fun formattedTracksByTrackGroup(trackGroup: TrackGroup): List<FormattedTrackOutput> {
        val recordingIds = trackGroup.tracks.map(Track::recordingId)
        val recordingDTOs = recordingService.findAllByIdWithCredits(recordingIds)
        val recordings = RecordingDTO1.fold(recordingDTOs, em)

        val workIds = recordingDTOs
            .map(RecordingDTO1::workId)
            .distinct()
        val workDTOs = workService.findAllByIdWithCredits(workIds)
        val works = WorkDTO1.fold(workDTOs, em)

        val movementIds = recordingDTOs
            .mapNotNull(RecordingDTO1::movementId)
            .distinct()
        val movements = movementService.findAllById(movementIds)

        val audioIds = recordingDTOs
            .mapNotNull(RecordingDTO1::audioId)
            .distinct()
        val audios = audioRepository.findAllById(audioIds)

        fun formatTrack(track: Track, recording: Recording): FormattedTrackTrackOutput {
            val output = FormattedTrackTrackOutput()
            output.id = track.id
            output.side = track.side
            output.order = track.order
            output.recording = with(FormattedTrackRecordingOutput()) {
                this.id = recording.id
                duration = recording.duration
                credits = recording.credits.map(recordingArtistMapper::toOutput)
                movement = recording.movementId
                    ?.let {
                        val movement = movements.find { m -> m.id == it }!!
                        MovementInTrack(movement.id, movement.titleCN, movement.title)
                    }
                audio = recording.audioId
                    ?.let {
                        val audio = audios.find { a -> a.id == it }!!
                        AudioDetailOutput(audio.id, audio.url, audio.fileName, audio.size, audio.format)
                    }
                this
            }

            return output
        }

        trackGroup.tracks.sortBy(Track::order)
        return trackGroup
            .tracks
            .segement {
                val recording = recordings.getValue(it.recordingId)
                if (recording.forWork)
                // 录音关联了作品，单独分开（可能存在连续两个作品相同)
                    UUID.randomUUID()
                else
                    recording.workId
            }
            .map { tracks ->
                val firstRecording = recordings.getValue(tracks.first().recordingId)
                val workId = firstRecording.workId!!
                val work = works.getValue(workId)
                val output = FormattedTrackOutput(
                    work.id, work.title, work.titleCN, work.catalogNumbers,
                    work.credits.map(workArtistMapper::toOutput)
                )
                if (firstRecording.forWork) {
                    output.track = formatTrack(tracks.first(), firstRecording)
                } else {
                    output.movementTracks = tracks.map { track ->
                        formatTrack(track, recordings.getValue(track.recordingId))
                    }
                }
                val masterTrackGroup = trackGroup.master
                output.trackGroup = trackGroupMapper.toTrackGroupReleaseOutput(trackGroup)
                if (masterTrackGroup != null) {
                    output.masterTrackGroup = trackGroupMapper.toTrackGroupReleaseOutput(masterTrackGroup)
                }
                output
            }
    }

    override fun swapTrackOrder(id: UUID, xId: UUID, yId: UUID) {
        val trackGroup = trackGroupService.findOrThrow(id)
        val trackX = trackGroup.tracks.firstOrNull { it.id == xId }
        if (trackX == null || trackX.releaseId != trackGroup.release.id)
            throw AppError.BadRequest.illegalOperate(msg = "音轨[$xId] 不属于该唱片")

        val trackY = trackGroup.tracks.firstOrNull { it.id == yId }
        if (trackY == null || trackY.releaseId != trackGroup.release.id)
            throw AppError.BadRequest.illegalOperate(msg = "音轨[$yId] 不属于该唱片")

        val temp = trackX.order
        trackX.order = trackY.order
        trackY.order = temp
        trackService.save(trackX)
        trackService.save(trackY)
    }

    override fun batchSwapTrackOrder(trackGroupId: UUID, source: List<Int>, dest: List<Int>) {
        val trackGroup = trackGroupService.findOrThrow(trackGroupId)
        source[0] == dest[0] && return

        val tracks = trackGroup.tracks.sortedBy(Track::order)

        val finalOrder = if (source[0] < dest[0])
            IntRange(source.last() + 1, dest.first() - 1) + dest + source
        else
            source + dest + IntRange(dest.last() + 1, source.first() - 1)

        var order = tracks[min(source.first(), dest.first())].order

        finalOrder.forEach {
            tracks[it].order = order++
        }

        trackService.saveAll(tracks)
    }

    override fun updateTrackInfo(id: UUID, trackId: UUID, body: UpdateTrackInput) {
        val track = trackService.findOrThrow(trackId)
        if (track.releaseId != id)
            throw AppError.BadRequest.illegalOperate(msg = "音轨[${track.id}] 不属于该唱片")
        trackGroupService.updateTrackGroupOrSubjectsFetched(track.trackGroup, false)
        body.side?.also { track.side = it }
        trackService.save(track)
    }


    private fun modifyIssue(release: Release, issues: List<IssueInput>) {
        val savedIssues = issues.map {
            val issue = Issue(
                issueTime = it.issueTime,
                regions = it.regionIds.map(countryService::findOrThrow).toMutableList(),
                release = release
            )
            issueService.save(issue)
        }
        patchCollection(release.issues, savedIssues, issueService::remove, issueService::create)
    }


    override fun backendListSearch(input: ReleaseBackendListInput): Page<Release> {
        val searchQuery = NativeSearchQueryBuilder()
        val booleanBuilder = BoolQueryBuilder()
        val q = input.q.removePunct()
        if (!q.isNullOrEmpty()) {
            if (input.key != null) {
                when (input.key) {
                    Key.TITLE ->
                        booleanBuilder.must(
                            if (q.containAnyChinese()) {
                                QueryBuilders.multiMatchQuery(q, "titleCN")
                                    .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                            } else {
                                QueryBuilders.multiMatchQuery(q, "title1", "title2")
                                    .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                            }
                        )
                    Key.CATALOG -> {
                        val catalogShouldBuilder = BoolQueryBuilder()
                        catalogShouldBuilder.should(
                            QueryBuilders.nestedQuery("catalogs",
                                QueryBuilders.wildcardQuery("catalogs.prefixNumbers",
                                    "${input.q!!}*"), ScoreMode.Avg)
                                .boost(1f)
                        ).should(
                            QueryBuilders.nestedQuery("catalogs",
                                QueryBuilders.termQuery("catalogs.prefixNumbers", input.q!!),
                                ScoreMode.Max
                            ).boost(@Suppress("MagicNumber") 3f)
                        ).should(QueryBuilders.nestedQuery("catalogs",
                            QueryBuilders.termQuery("catalogs.number",
                                "${input.q!!}*"), ScoreMode.Avg)
                            .boost(1f))
                        booleanBuilder
                            .must(catalogShouldBuilder)
                    }
                    Key.CREATEDBY ->
                        booleanBuilder.must(
                            QueryBuilders.matchQuery("createdBy.nickName", q)
                        )
                }
            } else {
                buildKeywordQuery(input.q!!, booleanBuilder)
            }
        }

        input.labelQ.isNotNullAndNotBlankThen {
            booleanBuilder.must(
                QueryBuilders.nestedQuery("catalogs",
                    QueryBuilders.multiMatchQuery(
                        it,
                        "catalogs.label.name",
                        "catalogs.label.nameCN"
                    ).type(MultiMatchQueryBuilder.Type.MOST_FIELDS),
                    ScoreMode.Avg))
        }

        input.genreId?.also {
            booleanBuilder.filter(QueryBuilders.termQuery("genreIds", it.toString()))
        }
        input.periodId?.also {
            booleanBuilder.filter(QueryBuilders.termQuery("periodId", it.toString()))
        }
        input.mediaId?.also {
            booleanBuilder.filter(QueryBuilders.termQuery("mediaId", it.toString()))
        }

        input.artistIds?.also {
            booleanBuilder.filter(
                QueryBuilders.nestedQuery("credits",
                    QueryBuilders.termsQuery("credits.artistId",
                        it.map { id -> id.toString() }
                    ),
                    ScoreMode.Avg
                )
            )
        }

        input.mainArtistIds?.also {
            booleanBuilder.filter(
                QueryBuilders.nestedQuery("mainArtists",
                    QueryBuilders.termsQuery("mainArtists.id",
                        it.map { id -> id.toString() }
                    ),
                    ScoreMode.Avg
                )
            )
        }

        input.workId?.also {
            booleanBuilder.filter(QueryBuilders.termsQuery("workIds", it.toString()))
        }

        input.labelId?.also {
            booleanBuilder.filter(QueryBuilders.nestedQuery("catalogs",
                QueryBuilders.termQuery("catalogs.label.id", it.toString()),
                ScoreMode.Avg
            ))
        }

        val boolQueryBuilder = BoolQueryBuilder()
            .must(QueryBuilders.termQuery("status", DocumentStatus.DRAFT.name))
            .must(QueryBuilders.termQuery("fromAdmin", false))
        val notUserDraft = BoolQueryBuilder()
        notUserDraft.mustNot(boolQueryBuilder)
        booleanBuilder.filter(notUserDraft)

        if (input.status.isNotEmpty()) {
            booleanBuilder.filter(QueryBuilders.termsQuery("status", input.status.map { it.name }))
        }
        input.master?.also {
            booleanBuilder.filter(QueryBuilders.termQuery("beMaster", it))
        }

        searchQuery.withQuery(booleanBuilder)
            .withPageable(input.pageable())
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
            .withSortings(input.sortings)
        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsRelease::class.java)
            .transform(this)
    }

    override fun frontendListSearch(input: ReleaseFilterInput): Page<Release> {
        return frontendListSearchTemplate(
            ALIAS_IDX_RELEASE,
            input,
            elasticsearchTemplate,
            EsRelease::class.java,
            "id")
        {
            it.withQuery(buildQueryBuilder(input))
            if (input.shouldForceSort()) {
                it.withSortings(input.sortings)
            } else {
                it.withSortings(listOf(SortBuilders.scoreSort(), SortBuilders.fieldSort("commendLevel").order(SortOrder.DESC)))
            }
            it
        }.transform(this)
    }

    override fun aggregationList(input: ReleaseFilterInput): List<TermsAggregationResult> {
        val searchQuery = NativeSearchQueryBuilder().withIndices("release")
        val query = buildQueryBuilder(input)
        searchQuery.withQuery(query)

        val catalogAgg = AggregationBuilders.nested("nestedCatalog", "catalogs")
        val prefixAgg = AggregationBuilders
            .terms("prefix")
            .field("catalogs.prefix")
            .size(@Suppress("MagicNumber") 1000)
        catalogAgg.subAggregation(prefixAgg)
        searchQuery.addAggregation(catalogAgg)

        val fields = listOf("genreIds",
            "styleIds",
            "mediaId",
            "periodId",
            "formId",
            "instrumentIds",
            "regionIds",
            "issueYears")
        fields.forEach {
            searchQuery.addAggregation(AggregationBuilders.terms(it).field(it).size(@Suppress("MagicNumber") 1000))
        }

        return elasticsearchTemplate.query(searchQuery.build()) {
            val catalog = it.aggregations.get<Nested>("nestedCatalog")
            val terms = catalog.aggregations.get<Terms>("prefix")
            val kcs = terms.buckets.map { bucket ->
                KeyCount(bucket.keyAsString.toUpperCase(), bucket.docCount)
            }
            val catalogResult = TermsAggregationResult("prefix", kcs)
            val termsResult = termsAggregationExtractor(it, fields)
                .toMutableList()
            termsResult.add(catalogResult)
            termsResult
        }
    }

    @Suppress("MagicNumber")
    private fun buildKeywordQuery(q: String, queryBuilder: BoolQueryBuilder) {
        queryBuilder.must(
            when {
                q.containAllChinese() -> QueryBuilders.multiMatchQuery(q, "titleCN")
                    .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                q.containAnyChinese() ->
                    QueryBuilders.boolQuery().should(
                        MultiMatchQueryBuilder(q)
                            .field("title1", 1.0f)
                            .field("title2", 1.0f)
                            .field("titleCN", 5f)
                            .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                            .minimumShouldMatch("2")
                    ).should(
                        QueryBuilders.nestedQuery("catalogs",
                            QueryBuilders.wildcardQuery("catalogs.prefixNumbers",
                                "${q.toLowerCase()}*"), ScoreMode.Avg)
                    )
                q.isNumber() -> QueryBuilders.boolQuery().should(
                    MultiMatchQueryBuilder(q)
                        .field("title1", 1.0f)
                        .field("title2", 1.0f)
                        .field("titleCN", 1.0f)
                        .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                ).should(
                    QueryBuilders.nestedQuery("catalogs",
                        QueryBuilders.wildcardQuery("catalogs.number",
                            "${q.toLowerCase()}*").boost(10f), ScoreMode.Avg).boost(10f)
                        .boost(10f)
                ).should(
                    QueryBuilders.nestedQuery("catalogs",
                        QueryBuilders.termQuery("catalogs.number", q.toLowerCase()).boost(30f), ScoreMode.Max).boost(30f)
                        .boost(30f)
                ).should(
                    QueryBuilders.nestedQuery("catalogs",
                        QueryBuilders.wildcardQuery("catalogs.prefixNumbers",
                            "${q.toLowerCase()}*").boost(10f), ScoreMode.Avg).boost(40f)
                        .boost(40f)
                )
                q.containAnyNumber() ->
                    QueryBuilders.boolQuery().should(
                        MultiMatchQueryBuilder(q)
                            .field("title1", 1.0f)
                            .field("title2", 1.0f)
                            .field("titleCN", 1.0f)
                            .type(MultiMatchQueryBuilder.Type.MOST_FIELDS)
                    ).should(
                        QueryBuilders.nestedQuery("catalogs",
                            QueryBuilders.wildcardQuery("catalogs.prefixNumbers",
                                "${q.toLowerCase()}*").boost(10f), ScoreMode.Avg).boost(10f)
                            .boost(10f)
                    ).should(
                        QueryBuilders.nestedQuery("catalogs",
                            QueryBuilders.termQuery("catalogs.prefixNumbers", q.toLowerCase())
                                .boost(30f), ScoreMode.Max)
                            .boost(30f)
                            .boost(30f)
                    )

                else -> QueryBuilders.boolQuery()
                    .should(
                        MultiMatchQueryBuilder(q)
                            .field("title1", 2.0f)
                            .field("title2", 2.0f)
                            .field("titleCN", 3.0f)
                            .type(MultiMatchQueryBuilder.Type.MOST_FIELDS))
                    .should(
                        QueryBuilders.nestedQuery("catalogs",
                            QueryBuilders.wildcardQuery("catalogs.prefixNumbers",
                                "${q.toLowerCase()}*").boost(1.5f)
                                .boost(1.5f),
                            ScoreMode.Avg).boost(1.5f))
            }
        )
    }

    private fun buildQueryBuilder(input: ReleaseFilterInput): QueryBuilder {
        val queryBuilder = BoolQueryBuilder()

        val q = input.q.removePunct()
        if (!q.isNullOrEmpty()) {
            buildKeywordQuery(input.q!!, queryBuilder)
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

        input.mediaId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("mediaId", it.toString()))
        }

        input.periodId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("periodId", it.toString()))
        }

        input.formId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("formId", it.toString()))
        }

        input.instrumentIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(QueryBuilders.termsQuery("instrumentIds",
                it.map { id -> id.toString() }
            ))
        }

        input.regionId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("regionIds", it.toString()))
        }

        input.issueYear?.also {
            queryBuilder.filter(QueryBuilders.termQuery("issueYears", it))
        }

        input.labelId?.also {
            queryBuilder.filter(QueryBuilders.nestedQuery("catalogs",
                QueryBuilders.termQuery("catalogs.label.id", it.toString()),
                ScoreMode.Avg
            ))
        }

        input.recordingIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(QueryBuilders.termsQuery("recordingIds",
                it.map { id -> id.toString() }
            ))
        }

        input.workIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(QueryBuilders.termsQuery("workIds",
                it.map { id -> id.toString() }
            ))
        }

        input.artistIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(
                QueryBuilders.nestedQuery("credits",
                    QueryBuilders.termsQuery("credits.artistId",
                        it.map { id -> id.toString() }
                    ),
                    ScoreMode.Avg
                )
            )
        }

        input.mainArtistIds?.also {
            queryBuilder.filter(
                QueryBuilders.nestedQuery("mainArtists",
                    QueryBuilders.termsQuery("mainArtists.id",
                        it.map { id -> id.toString() }
                    ),
                    ScoreMode.Avg
                )
            )
        }

        input.professionIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(
                QueryBuilders.nestedQuery("credits",
                    QueryBuilders.termsQuery("credits.professionId",
                        it.map { id -> id.toString() }),
                    ScoreMode.Avg
                )
            )
        }

        input.billboardIds.isNotNullAndNotEmptyThen {
            queryBuilder.filter(QueryBuilders.termsQuery("billboardIds",
                it.map { id -> id.toString() }
            ))
        }

        input.firstLetter.isNotNullAndNotBlankThen {
            queryBuilder.filter(QueryBuilders.termsQuery("firstLetter", it))
        }

        input.prefix.isNotNullAndNotBlankThen {
            queryBuilder.filter(QueryBuilders.nestedQuery("catalogs",
                QueryBuilders.termQuery("catalogs.prefix", it),
                ScoreMode.Avg
            ))
        }

        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        input.master?.also {
            queryBuilder.filter(QueryBuilders.termQuery("beMaster", true))
        }

        val issueYearRange = input.getYearRange()
        if (issueYearRange != null) {
            queryBuilder.filter(QueryBuilders.rangeQuery("issueYears")
                .from(issueYearRange.first, true)
                .to(issueYearRange.second, true)
            )
        }

        return queryBuilder
    }

    override fun allCredits(release: Release): Set<ArtistProfession> {
        val releaseArtists = findReleaseArtist(release)
        val recordingArtists = findRecordingArtist(release)
        val workArtists = findWorkArtist(release)
        val resultArtistProfessions = mutableSetOf<ArtistProfession>()
        resultArtistProfessions.addAll(releaseArtists)
        resultArtistProfessions.addAll(recordingArtists)
        resultArtistProfessions.addAll(workArtists)
        return resultArtistProfessions
    }

    override fun indexToEs(release: Release) {
        logger.debug("Index Release[${release.id}] to elasticsearch")
        val esRelease = releaseMapper.toEs(release)
        esRelease.credits = allCredits(release).map {
            CreditDoc(it.artistId, it.professionId ?: UUIDConst.DEFAULT_UUID)
        }
        esRelease.recordingIds = findAllRecordingIdsThroughTrack(
            release.id).let {
            if (it.isEmpty()) emptyList() else it.map(UUID::fromString)
        }

        esRelease.workIds = findAllWorkIdsThroughTrack(
            release.id).let {
            if (it.isEmpty()) emptyList() else it.map(UUID::fromString)
        }

        releaseEsRepository.index(esRelease)
    }

    @Async
    override fun asyncIndexToEs(releases: List<Release>): CompletableFuture<Unit> {
        releases.forEach(::indexToEs)
        return CompletableFuture.completedFuture(Unit)
    }

    override fun freshCollectCount(release: Release, latestCount: Long) {
        release.collectCount = latestCount
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun freshSaleCount(release: Release, saleService: SaleService) {
        release.saleCount = saleService.countByRelease(release)
        val saved = save(release)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun pageByCreator(user: User, keyword: String?, pageable: Pageable): Page<Release> {
        var eq = qRelease.deleted.isFalse
            .and(qRelease.createdBy.eq(user))
        keyword?.also { eq = eq.and(qRelease.titleCN.like("%$it%")) }
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
        val monthDataDifference = countByMonth(
            nowTime.year,
            nowTime.monthValue
        ) - countByMonth(lastMonth.year, lastMonth.monthValue)
        return ReleaseDataAnalysisOutput(count(), yesterdayDataDifference, monthDataDifference)
    }

    override fun onSaleReleaseDataAnalysis(): ReleaseDataAnalysisOutput {
        val nowTime: ZonedDateTime = ZonedDateTime.now()
        val yesterday = ZonedDateTime.now().plusDays(-1)
        val yesterdayDataDifference = onSaleCountByDay(
            nowTime.year,
            nowTime.monthValue,
            nowTime.dayOfMonth
        ) - onSaleCountByDay(yesterday.year, yesterday.monthValue, yesterday.dayOfMonth)
        val lastMonth = ZonedDateTime.now().plusMonths(-1)
        val monthDataDifference = onSaleCountByMonth(
            nowTime.year,
            nowTime.monthValue
        ) - onSaleCountByMonth(lastMonth.year, lastMonth.monthValue)
        return ReleaseDataAnalysisOutput(count(), yesterdayDataDifference, monthDataDifference)
    }

    override fun completionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("release",
            listOf("titleCNCompletion", "titleCompletion", "catalogCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        return CompletionSuggestResult("release", options)
    }

    override fun backendCompleteionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("release",
            listOf("titleCNCompletion", "titleCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        val option = KeyOption("title", options = options.map { it.options }.flatten().distinct())
        return CompletionSuggestResult("release", listOf(option))
    }

    override fun similar(release: Release, pageable: Pageable): Page<Release> {
        val queryBuilder = BoolQueryBuilder()

        //过滤自身ID
        queryBuilder.mustNot(QueryBuilders.idsQuery().addIds(release.stringId))

        release.periodId?.also {
            queryBuilder.should(QueryBuilders.termQuery("periodId", it.toString()))
        }

        queryBuilder.should(
            QueryBuilders.termsQuery("workIds", findAllWorkIdsThroughTrack(release.id))
        )
        queryBuilder.should(
            QueryBuilders.termsQuery("artistIds", release.artistIds.map(UUID::toString))
        )

        release.formId?.also {
            queryBuilder.should(QueryBuilders.termQuery("formId", it.toString()))
        }

        if (release.genres.isNotEmpty()) {
            queryBuilder.should(
                QueryBuilders.termsQuery("genreIds", release.genreIds.map(UUID::toString))
            )
        }

        if (release.styles.isNotEmpty()) {
            queryBuilder.should(
                QueryBuilders.termsQuery("styleIds", release.styleIds.map(UUID::toString))
            )
        }
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        queryBuilder.filter(QueryBuilders.termQuery("beMaster", true))

        val searchQuery = NativeSearchQueryBuilder()
        searchQuery.withQuery(queryBuilder)
            .withPageable(pageable)
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsRelease::class.java)
            .transform(this)
    }


    override fun updateRecordingsArtists(id: UUID, input: UpdateRecordingsArtistInput) {
        val release = findUnarchivedOrThrow(id)
        val recordings = input.recordingIds.map {
            if (!release.hasRecording(it))
                throw AppError.BadRequest.illegalOperate(msg = "录音[$it]不属于该唱片")

            recordingService.findExistsOrThrow(it)
        }

        recordings.forEach {
            recordingService.updateArtistsAndSyncReleaseArtist(it, input.recordingArtists)
        }
    }


    override fun addRecordingsArtist(id: UUID, input: UpdateRecordingArtistInput) {
        val release = findUnarchivedOrThrow(id)
        release.recordings.forEach {
            recordingService.addArtistAndSyncReleaseArtist(it, input)
        }
    }

    override fun copy(originId: UUID): Release {
        val origin = findUnarchivedOrThrow(originId)
        return copy(origin)
    }

    private fun copy(origin: Release): Release {
        var new = origin.copy()
        new = save(new)

        origin.catalogs.forEach {
            catalogRepository.save(
                Catalog(it.label, new, it.number)
            )
        }
        origin.credits.forEach {
            releaseArtistRepository.save(
                ReleaseArtist(new, it.artist, it.order, it.main)
            )
        }

        origin.genres.forEach { new.genres.add(it) }
        origin.styles.forEach { new.styles.add(it) }
        origin.instruments.forEach { new.instruments.add(it) }


        syncReleaseArtistFromWorkAndRecording(new.id)
        return new
    }

    override fun copyAsSameEdition(originId: UUID): Release {
        val origin = findUnarchivedOrThrow(originId)
        return copyAsSameEdition(origin)
    }

    private fun copyAsSameEdition(origin: Release): Release {
        val masters: List<Release> =
        // 如果origin本来就是主版本，则直接直接关联到origin
            if (origin.beMaster) listOf(origin)
            // 如果origin是一个子版本，则关联到origin的主版本
            else origin.masters

        val new = relationToMaster(copy(origin), masters)
        new.calcCompleteness()
        eventPublisher.publishEvent(EntityEvent.created(new))
        return new
    }

    /**
     * 复制到编辑区时
     * 只复制基本信息和trackGroup
     * 无需建立关联关系，所以不用调relationToMaster
     */
    override fun copyForEdit(id: UUID): Release {
        val release = findUnarchivedOrThrow(id)
        return copyForEdit(release)
    }

    private fun copyForEdit(release: Release): Release {
        release.mustPublished()
        var new = copy(release)
        release.trackGroups.forEach {
            new.trackGroups.add(trackGroupService.copy(it, new))
        }
        new.originId = release.id
        new = updateStatus(release, DocumentStatus.DRAFT)
        new.subStatus = DocumentSubStatus.DRAFT_USER_EDIT
        new = save(new)

        eventPublisher.publishEvent(EntityEvent.created(new))
        return new
    }

    /**
     * 将某个子版本与若干主版本进行关联
     * 关联的本质是复制track同时建立关联关系
     * 因此如果要完整的copy一张唱片还需要调用该方法
     * 需要刷新editionCount
     */
    override fun relationToMaster(subjectId: UUID, masterIds: List<UUID>, force: Boolean): Release {
        val subject = findUnarchivedOrThrow(subjectId)
        val masters = findAllByIdInAndStatusNot(masterIds)
        return relationToMaster(subject, masters, force)
    }

    private fun relationToMaster(subject: Release, masters: List<Release>, force: Boolean = false): Release {
        if (subject.beMaster && subject.subjectCount > 0) throw AppError.BadRequest.illegalOperate(msg = "唱片含有子版本，不允许关联别的子版本")
        val oldMasters = mutableListOf<Release>()
        oldMasters.addAll(subject.masters)
        val subjectTrackGroups = mutableListOf<TrackGroup>()
        // 这是为了保证当一张唱片只关联了一个主版本时，确保有且只有一个音轨组且是继承自主版本的
        // 如果一张唱片之前没有关联过主版本，但同时它拥有自己的trackGroup，那么关联时会被覆盖，此处给出提示
        // 若需要强制关联，则需要发起二次请求，并带上force参数
//        if (subject.masters.isEmpty() && subject.trackGroups.isNotEmpty()) {
//             当强制关联时，需要先把旧的trackGroup清空
//            if (force) {
//                trackService.deleteAll(subject.tracks)
//                trackGroupService.deleteAll(subject.trackGroups)
//                subject.trackGroups.clear()
//            }
//            else throw AppError.BadRequest.illegalOperate(msg = "唱片已有音轨，若关联将会导致原音轨被覆盖")
//        }
        subject.beMaster = false
        for (master in masters) {
            // 获取到主唱片MasterTrackGroup
            val masterTrackGroup = trackGroupService.getFirstTrackGroup(master)
            // 拷贝MasterTrackGroup到SubjectTrackGroup
            val subjectTrackGroup = trackGroupService.copy(masterTrackGroup, subject)
            // 将SubjectTrackGroup与MasterTrackGroup建立关联关系
            subjectTrackGroup.master = masterTrackGroup
            subjectTrackGroups.add(subjectTrackGroup)
        }
        // 更新数据库
        trackGroupService.saveAll(subjectTrackGroups)
        // 建立release关联关系
        subject.masters.addAll(masters)
        val result = save(subject)
        refreshEditionCount(result.id)
        oldMasters.map(Release::id).forEach(this::refreshEditionCount)

        syncReleaseArtistFromWorkAndRecording(subject.id)
        return result
    }

    override fun relationToMaster(subjectIds: List<UUID>, masterId: UUID, force: Boolean): List<Release> {
        val subjects = findAllByIdInAndStatusNot(subjectIds)
        val master = findUnarchivedOrThrow(masterId)
        return relationToMaster(subjects, master, force)
    }

    private fun relationToMaster(subjects: List<Release>, master: Release, force: Boolean = false): List<Release> {
        // 记录原来subject的masters，用于刷新editionCount
        val oldMasters = mutableListOf<Release>()
        // 获取到主唱片MasterTrackGroup
        val masterTrackGroup = trackGroupService.getFirstTrackGroup(master)
        val subjectTrackGroups = mutableListOf<TrackGroup>()
        for (subject in subjects) {
            subject.beMaster = false
            // 建立关联关系
            subject.masters.add(master)
            oldMasters.addAll(subject.masters)
            // 拷贝MasterTrackGroup到SubjectTrackGroup
            val subjectTrackGroup = trackGroupService.copy(masterTrackGroup, subject)
            // 将SubjectTrackGroup与MasterTrackGroup建立关联关系
            subjectTrackGroup.master = masterTrackGroup
            subjectTrackGroups.add(subjectTrackGroup)
        }
        // 更新数据库
        trackGroupService.saveAll(subjectTrackGroups)

        val result = saveAll(subjects)
        result.map(Release::id).forEach(this::refreshEditionCount)
        oldMasters.map(Release::id).forEach(this::refreshEditionCount)

        subjects.map(Release::id).forEach(this::syncReleaseArtistFromWorkAndRecording)
        return result
    }

    /**
     * 除了解除关联以外还要刷新相关唱片的editionCount
     */
    override fun clearRelationToMaster(subjectId: UUID, masterIds: List<UUID>): Release {
        val subject = findUnarchivedOrThrow(subjectId)
        val masters = findAllByIdInAndStatusNot(masterIds)
        subject.removeMasters(masterIds)
        // releaseGroup解除关联关系
        for (m in masters) {
            trackGroupService.clearRelation(subject, m)
        }
        refreshEditionCount(subject.id)

        // 一个子版本解除关联后，会影响到所解除关联的主版本的同版本数量
        // 以及影响到所解除关联的主版本的所有子版本的同版本数量
        masters.map(Release::id).forEach(this::refreshEditionCount)
        masters.flatMap { it.subjects.map(Release::id) }.forEach(this::refreshEditionCount)

        syncReleaseArtistFromWorkAndRecording(subjectId)
        return save(subject)
    }

    override fun syncSubjectAndSyncReleaseArtist(masterId: UUID): List<Release> {
        val master = findUnarchivedOrThrow(masterId)
        val masterTrackGroup = trackGroupService.getFirstTrackGroup(master)
        // 获取masterTrackGroup的subjects
        val subjectTrackGroups = masterTrackGroup.subjects

        // 检查所有子版本是否已经是同步状态, 找出未同步的子版本
        val unFetched = mutableListOf<TrackGroup>()
        for (subjectTrackGroup in subjectTrackGroups) {
//            if (subjectTrackGroup.fetched != null && !subjectTrackGroup.fetched!!) {
            unFetched.add(subjectTrackGroup)
//            }
        }

        // 如果没有未同步的子版本则直接返回一个空数组
        if (unFetched.size == 0) {
            return emptyList()
        }

        // 主版本有且只有一个trackGroup
        val trackGroup = trackGroupService.getFirstTrackGroup(master)
        if (trackGroup.tracks.size == 0) {
            throw TrackGroupException("当前主版本下没有音轨，无需同步")
        }


        for (subjectTrackGroup in unFetched) {
            // 清理原来的track
            trackService.deleteAll(subjectTrackGroup.tracks)
            subjectTrackGroup.tracks.clear()

            // 从主版本TrackGroup复制track并添加到子版本TrackGroup
            subjectTrackGroup.tracks = trackService.copyAndPersist(trackGroup.tracks, subjectTrackGroup.release, subjectTrackGroup)
            subjectTrackGroup.fetched = true
        }

        val releases = trackGroupService
            .saveAll(unFetched)
            .map { it.release }
            .distinct()

        // 同版本唱片同步艺术家列表。
        releases.map(Release::id).forEach(this::syncReleaseArtistFromWorkAndRecording)

        return releases
    }

    /**
     * 查找相同版本
     * 以子版本查找则结果不包含自身
     */
    override fun sameEditions(id: UUID, status: List<DocumentStatus>): MutableList<Release> {
        val release = findUnarchivedOrThrow(id)
        return sameEditions(release, status)
    }


    /**
     * 同版本概念：
     * - 主体是主版本
     *    * 有且只有关联主体的子版本为同版本。
     * - 主体是子版本
     *    * 关联的主版本只有一个的情况，有且只关联同主版本的子版本和包括该主版本算是同版本。
     *    * 关联的主版本有多个情况，关联主版本的情况跟主体一样的子版本算是同版本。
     *
     * @since 2019年7月30日, PM 04:09:11
     */
    fun sameEditions(release: Release, status: List<DocumentStatus>): MutableList<Release> {
//        val result: MutableList<Release>
//        when {
//            release.beMaster -> {
//                result = findByMastersIdAndStatusInAndUnDeleted(listOf(release.id), 1, status.map { it.ordinal })
//            }
//            release.masters.isNotEmpty() -> {
//                val masterIds = release.masters.map { it.id }
//                result = findByMastersIdAndStatusInAndUnDeleted(masterIds, masterIds.size, status.map { it.ordinal })
//                result.removeIf { it.id == release.id }
//                // 如果主版本只有一个说明同版本都没有关联多个主版本
//                // 那么主版本也是同版本
//                if (release.masters.size == 1) result.addAll(release.masters)
//            }
//            else -> return mutableListOf()
//        }
//        return result.filter { status.contains(it.status) }.toMutableList()
        return if (release.beMaster) {
            // 如果该唱片是主版本
            val subReleasesContainThisMaster =
                findAll(qRelease.deleted.isFalse
                    .and(qRelease.masters.contains(release))
                    .and(qRelease.id.ne(release.id)))
            subReleasesContainThisMaster
                .filter {
                    it.masters.size == 1
                }
                .filter {
                    status.contains(it.status)
                }.toMutableList()
        } else {
            // 如果是子版本
            val masters = release.masters
            val pre = qRelease.deleted.isFalse
                .and(qRelease.masters.contains(masters.first())
                    .and(qRelease.id.ne(release.id)))
            val findAll = findAll(pre)
            val filterSize = findAll
                .filter {
                    it.masters.size == masters.size
                }
            val filterMasters = filterSize
                .filter {
                    it.masters.containsAll(masters)
                }
            val filterStatus = filterMasters
                .filter {
                    status.contains(it.status)
                }
            val list = filterStatus.toMutableList()
            if (masters.size == 1) {
                list.addAll(masters)
            }
            list
        }
    }

    override fun setAsMaster(id: UUID) {
        val release = findUnarchivedOrThrow(id)
        if (!release.canSetAsMaster()) throw AppError.BadRequest.default(msg = "关联多个主版本的唱片不允许设置为主版本")
        // sameEditions不包含本身
        var sameEditions = sameEditions(release, DocumentStatus.values().toList())
        // 如果sameEdition为空数组则抛异常
        if (sameEditions.isEmpty()) throw AppError.BadRequest.default(msg = "该唱片没有相同版本")
        // 有且只有一个master
        val master = release.masters.first()
        // master和release互相交换各自的masters、subjects
        // release是即将要设置为主版本的唱片，以下是对其进行一些预处理
        release.masters.clear()
        release.beMaster = true
        save(release)

        var (trackGroup, shouldRemove) =
            if (release.trackGroups.size > 1) {
                // trackGroups的数量大于一，说明它既有继承自主版本的音轨，也有自己的音轨
                // 那么此时应该把两个trackGroup的音轨合并
                // 合并后release.trackGroup会剩下被合并掉的trackGroup
                trackGroupService.mergeTrackGroup(release.trackGroups)
            } else {
                // 这里取first一定不为null，因为release只关联了的一个主版本的情况下
                // 那么至少会有一个trackGroup
                Pair(release.trackGroups.first(), mutableListOf())
            }

        trackGroup.master = null
        trackGroup.fetched = null
        trackGroup = trackGroupService.save(trackGroup)

        // 处理subjectTrackGroup
        val subjectTrackGroups = mutableListOf<TrackGroup>()
        val masterTrackGroup = trackGroupService.getFirstTrackGroup(master)
//        masterTrackGroup.subjects.remove(trackGroup)
        subjectTrackGroups.add(masterTrackGroup)
        subjectTrackGroups.addAll(masterTrackGroup.subjects)
        subjectTrackGroups.removeAll(shouldRemove)
        subjectTrackGroups
            .filter {
                it.id != trackGroup.id
            }.forEach {
                it.master = trackGroup
                it.fetched = false
            }
        trackGroupService.saveAll(subjectTrackGroups)

        // 处理所有的同版本包括原来的master
        // 先清空关联关系，数据库在中间表删除相应条目
        sameEditions.forEach { it.masters.clear() }
        sameEditions = saveAll(sameEditions)
        // 再重新建立新的关系
        sameEditions.forEach {
            // 1、把release的subjects设置为sameEditions（里面包括原来的master）
            it.beMaster = false
            it.masters = mutableListOf(release)
        }
        sameEditions.forEach { it.updatedAt = ZonedDateTime.now() }
        val saved = saveAll(sameEditions)

        saved.map(Release::id).forEach(this::syncReleaseArtistFromWorkAndRecording)

//        saved.forEach { eventPublisher.publishEvent(EntityEvent.updated(it)) }
    }


    override fun updateTrackGroupOrder(id: UUID, trackGroupId: UUID, move: TrackGroup.Move) {
        val release = findUnarchivedOrThrow(id)
        val trackGroup = trackGroupService.findById(trackGroupId).get()
        val sourceIndex = release.trackGroups.indexOf(trackGroup)
        val targetIndex =
            when (move) {
                TrackGroup.Move.UP -> {
                    if (sourceIndex == 0) AppError.BadRequest.illegalOperate("已是第一位，无需上移")
                    sourceIndex - 1
                }
                TrackGroup.Move.DOWN -> {
                    if (sourceIndex == release.trackGroups.size - 1) AppError.BadRequest.illegalOperate("已是最后一个，无需下移")
                    sourceIndex + 1
                }
            }
        val sourceOrder = trackGroup.order
        trackGroup.order = release.trackGroups[targetIndex].order
        release.trackGroups[targetIndex].order = sourceOrder
        trackGroupService.save(trackGroup)
        trackGroupService.save(release.trackGroups[targetIndex])
    }

    override fun refreshEditionCount(id: UUID) {
        val releaseOption = findById(id)
        if (releaseOption.isPresent) {
            val release = releaseOption.get()
            val sameEditions = sameEditions(release, DocumentStatus.values().toMutableList().also { it.remove(DocumentStatus.ARCHIVED) })
            if (release.status != DocumentStatus.ARCHIVED) {
                sameEditions.add(release)
            }
            refreshEditionCount(sameEditions.map(Release::id))
        }
    }

    // 处理的是同版本唱片的editionCount
    override fun refreshEditionCount(sameEditionIds: List<UUID>) {
        val sameEditions = findAllById(sameEditionIds)
        val backEditionCount = sameEditions.count { it.status != DocumentStatus.ARCHIVED }.toLong()
        val unPublishedCount = sameEditions.count { it.status != DocumentStatus.PUBLISHED }.toLong()
        val frontEditionCount = backEditionCount - unPublishedCount

        sameEditions.forEach {
            it.backEditionCount = backEditionCount
            it.frontEditionCount = frontEditionCount
        }

        val saved = saveAll(sameEditions)
        saved.forEach { eventPublisher.publishEvent(EntityEvent.updated(it)) }
    }

    override fun getMasters(id: UUID): MutableList<Release> {
        val release = findUnarchivedOrThrow(id)
        if (release.beMaster && release.subjects.isNotEmpty()) {
            return mutableListOf()
        }
        return release.trackGroups.map { it.master!!.release }.toMutableList()
    }

    override fun searchByImage(image: ByteArray): List<Pair<SearchResult, Release>> {
        var searchResults = imageSearchService.similarSearch(image)

        searchResults = searchResults.distinctBy { it.brief["releaseId"] }   // 可能一张唱片的多张图片都被搜索出来
        val releaseIds = searchResults.map {
            UUID.fromString(it.brief["releaseId"].toString())
        }
        val releases = findAllById(releaseIds)

        return searchResults
            .map {
                it to releases.find { release ->
                    release.id.toString() == it.brief["releaseId"]
                }!!
            }.filter { (result, release) ->
                if (release.deleted) { // 唱片已删除，图片索引也跟着删除
                    imageSearchService.similarDelete(result.brief["imagePath"].toString())
                    false
                } else
                    release.published
            }
    }

    override fun afterDelete(targetId: UUID) {
        /*同时删除动态*/
        eventPublisher.publishEvent(DeleteTimeLineEvent(targetId))
    }
}
