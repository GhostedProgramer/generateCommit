package com.musicbible.service

import com.alibaba.fastjson.JSONObject
import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.exceptions.ClientException
import com.aliyuncs.profile.DefaultProfile
import com.aliyuncs.vod.model.v20170321.CreateUploadVideoRequest
import com.aliyuncs.vod.model.v20170321.CreateUploadVideoResponse
import com.aliyuncs.vod.model.v20170321.GetPlayInfoRequest
import com.aliyuncs.vod.model.v20170321.GetPlayInfoResponse
import com.aliyuncs.vod.model.v20170321.GetVideoInfoRequest
import com.aliyuncs.vod.model.v20170321.GetVideoInfoResponse
import com.aliyuncs.vod.model.v20170321.RefreshUploadVideoRequest
import com.aliyuncs.vod.model.v20170321.RefreshUploadVideoResponse
import com.boostfield.extension.toDate
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.extension.withSortings
import com.boostfield.spring.payload.PageQuery
import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.CompletionSuggestService
import com.boostfield.spring.service.KeyOption
import com.boostfield.spring.service.SearchService
import com.musicbible.aspect.Locked
import com.musicbible.config.properties.VodConfig
import com.musicbible.es.model.EsVideo
import com.musicbible.es.repository.VideoEsRepository
import com.musicbible.event.DeleteTimeLineEvent
import com.musicbible.event.EntityEvent
import com.musicbible.event.TimeLineEvent
import com.musicbible.event.TimingPublishVideoEvent
import com.musicbible.event.VideoEventType
import com.musicbible.event.VideoNotificationEvent
import com.musicbible.event.virtualdata.VirtualPlayDataEvent
import com.musicbible.extension.transform
import com.musicbible.mapper.video.ChangeSendTimeInput
import com.musicbible.mapper.video.SearchInput
import com.musicbible.mapper.video.UpdateAttributeOutput
import com.musicbible.mapper.video.VideoListInput
import com.musicbible.mapper.video.VideoMapper
import com.musicbible.mapper.video.WebUpdateAttributeOutput
import com.musicbible.mapper.video.WebUpdatePublishedVideoInput
import com.musicbible.mapper.video.WebUpdateVideoInput
import com.musicbible.mapper.vod.TransCodeComplete
import com.musicbible.mapper.vod.UploadComplete
import com.musicbible.model.Artist
import com.musicbible.model.DocumentStatus
import com.musicbible.model.DocumentSubStatus
import com.musicbible.model.ModelEnum
import com.musicbible.model.QVideo
import com.musicbible.model.Release
import com.musicbible.model.User
import com.musicbible.model.Video
import com.musicbible.model.Work
import com.musicbible.repository.VideoRepository
import com.musicbible.service.check.CheckService
import com.querydsl.core.BooleanBuilder
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.RangeQueryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
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
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture


interface VideoVodService : VideoService {
    fun getVideoPlayInfo(videoId: String): GetPlayInfoResponse
    fun createUploadVideo(title: String, fileName: String): CreateUploadVideoResponse
    fun vodACKConfigure(): Pair<String, String>
    fun vodRegionId(): String
    fun vodCallbackURL(): String
    fun vodCallbackType(): String
    fun vodTemplateGroupId(): String
    fun fileUploadCompleteHandler(uploadComplete: UploadComplete)
    fun transCodeCompleteHandler(transCodeComplete: TransCodeComplete)
    fun refreshUploadVideo(videoId: String): RefreshUploadVideoResponse
    fun updateVodInfo(video: Video, videoId: String, title: String, fileName: String)
    fun refreshUploadStatus(id: UUID): String
    fun getVideoInfo(videoId: String): GetVideoInfoResponse
    fun transCodeCompleteHandlerWithoutPublish(transCodeComplete: TransCodeComplete)
    fun refreshUploadStatusWithoutPublish(id: UUID): String
}

class VideoException(val msg: String) : java.lang.RuntimeException(msg)

interface VideoService : DocumentService<Video>, VideoRepository, SearchService<EsVideo, UUID>, CompletionSuggestService {
    override val modelName: String
        get() = "视频"

    /**
     * 更新接口
     */
    fun updateName(id: UUID, name: String)

    fun updateURL(id: UUID, url: String)
    fun updateImages(id: UUID, images: Array<String>)
    fun updateDescription(id: UUID, description: String)
    fun updateArtists(id: UUID, artists: List<Artist>)
    fun updateReleases(id: UUID, releases: List<Release>)
    fun updateWorks(id: UUID, works: List<Work>)
    fun updateAttribute(id: UUID, input: UpdateAttributeOutput)
    fun updateAttribute(id: UUID, input: WebUpdateAttributeOutput)
    fun updateWeight(id: UUID, commendLevel: Int)

    /**
     * 字段刷新
     */
    fun increasePlayCount(video: Video)

    fun increasePlayCountAndStartQuartz(video: Video)

    fun refreshCollectCount(video: Video, count: Long)
    fun refreshShareCount(video: Video, count: Long)
    fun refreshLikeCount(video: Video, count: Long)
    fun refreshCommentCount(video: Video, count: Long)

    /**
     * 查询接口
     */
    fun list(input: SearchInput): Page<Video>

    fun listForBackend(input: SearchInput): Page<Video>

    fun listForBackend(input: VideoListInput): Page<Video>

    fun personalList(user: User, q: String?, page: PageQuery): Page<Video>
    fun otherList(user: User, input: PageQuery): Page<Video>

    /**
     * ES
     */
    fun indexToEs(video: Video)

    fun indexToEs(videos: List<Video>)
    fun asyncIndexToEs(videos: List<Video>): CompletableFuture<Unit>
    fun findByRelease(id: UUID): List<Video>
    fun findByArtist(id: UUID): List<Video>
    fun findByWork(id: UUID): List<Video>
    fun drafts(user: User, q: String?, page: Pageable): Page<Video>
    fun nextEsVideo(currentVideoId: UUID, sort: String?, key: String?): Video?

    /**
     * 后台删除视频
     * - 如果是用户创建的视频，则发送通知并软删
     */
    fun deleteAndNotify(id: UUID, reason: String)

    /*在已发布的视频被删除时执行*/
    fun afterPublishedBeDelete(target: Video)

    fun publish(user: User, id: UUID)

    fun suppress(user: User, id: UUID)

    fun softDelete(user: User, id: UUID)

    fun deleteAndNotify(user: User, id: UUID, reason: String)

    fun updateAttribute(user: User, id: UUID, input: UpdateAttributeOutput)

    fun updateImages(user: User, id: UUID, images: Array<String>)

    fun updateName(user: User, id: UUID, name: String)

    fun updateWeight(user: User, id: UUID, commendLevel: Int)

    fun updateDescription(user: User, id: UUID, description: String)

    fun updateArtists(user: User, id: UUID, artists: List<Artist>)

    fun updateReleases(user: User, id: UUID, releases: List<Release>)

    fun updateWorks(user: User, id: UUID, works: List<Work>)

    fun editDraft(user: User, id: UUID, input: WebUpdateVideoInput)

    fun commitFromDraft(user: User, id: UUID)

    fun editPublished(user: User, id: UUID, input: WebUpdatePublishedVideoInput)

    fun updateSendTime(id: UUID, input: ChangeSendTimeInput, user: User)
}

@Service("videoService")
@Transactional
class VideoServiceImpl(
    @Lazy @Autowired val videoCheckService: CheckService,
    @Lazy @Autowired val videoVodService: VideoVodService,
    @Autowired val releaseService: ReleaseService,
    @Autowired val artistService: ArtistService,
    @Autowired val workService: WorkService,
    @Autowired val videoEsRepository: VideoEsRepository,
    @Autowired val videoRepository: VideoRepository,
    @Autowired val videoMapper: VideoMapper,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val esIndexService: EsIndexService<Video, EsVideo>,
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val applicationEventPublisher: ApplicationEventPublisher,
    @Autowired val quartzService: QuartzService
) : VideoService, VideoRepository by videoRepository {

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun editPublished(user: User, id: UUID, input: WebUpdatePublishedVideoInput) {
        val video = findExistsAndOwnOrThrow(id, user)

        if (video.status != DocumentStatus.PUBLISHED) {
            throw AppError.BadRequest.default(msg = "无法编辑非发布视频")
        }

        val images = input.images
        val name = input.name
        if (name.isNullOrBlank()
            && input.description.isNullOrBlank()
            && images.isNullOrEmpty()
            && input.artists == null
            && input.releases == null
            && input.works == null) {
            throw AppError.BadRequest.default(msg = "参数不能全为空")
        }

        if (name != null && name.isBlank()) {
            throw AppError.BadRequest.default(msg = "名称不能为空")
        }
        if (images != null && images.isEmpty()) {
            throw AppError.BadRequest.default(msg = "视频不能为空")
        }

        name?.also { video.name = it }
        input.description?.also { video.description = it }
        images?.also { video.images = it }
        input.artists?.also {
            video.artists.clear()
            video.artists.addAll(it.map(artistService::findExistsOrThrow))
        }
        input.works?.also {
            video.works.clear()
            video.works.addAll(it.map(workService::findExistsOrThrow))
        }
        input.releases?.also {
            video.releases.clear()
            video.releases.addAll(it.map(releaseService::findExistsOrThrow))
        }
        save(video)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun commitFromDraft(user: User, id: UUID) {
        val video = findExistsAndOwnOrThrow(id, user)
        //校验参数
        if (video.fileName.isBlank()
            || video.name.isBlank()
            || video.videoId.isNullOrBlank()
            || video.images.isEmpty()) {
            throw AppError.BadRequest.default(msg = "请检查必填项后重新提交")
        }

        //检查视频状态
        if (video.isTranscodeFail || video.subStatus == DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL) {
            throw AppError.BadRequest.default(msg = "无法对转码失败的视频提交审核, 请重新上传视频后再试。")
        }
        if (video.status != DocumentStatus.DRAFT) {
            throw AppError.BadRequest.default(msg = "视频的状态有误: ${video.status}, 无法提交。")
        }
        if (!video.readyToCommitFromDraft()) {
            throw AppError.BadRequest.default(msg = "视频不符合提交状态。")
        }

        //判断视频上传状态, 必须为已上传。
        mustUploaded(video.videoId!!)

        video.subStatus = DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS
        val vodStatus = videoVodService.refreshUploadStatusWithoutPublish(video.id)

        //如果已经转码成功，则直接提交审核。
        if (vodStatus == "Normal") {
            videoCheckService.commit(video, user, null)
        }
        save(video)
    }

    private fun mustUploaded(videoId: String) {
        val videoInfo = videoVodService.getVideoInfo(videoId)
        when (videoInfo.video.status) {
            "Uploading" -> throw AppError.BadRequest.default(msg = "视频未成功上传，无法保存")
            "UploadFail" -> throw AppError.BadRequest.default(msg = "视频未成功上传，无法保存")
            "TranscodeFail" -> throw AppError.BadRequest.default(msg = "视频转码失败，请重新更换视频源后重试。")
            "Blocked" -> throw AppError.BadRequest.default(msg = "视频已被屏蔽，请更换视频后重试。")
        }
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun editDraft(user: User, id: UUID, input: WebUpdateVideoInput) {
        //校验参数
        val videoId = input.videoId
        if (input.fileName.isNullOrBlank()
            && input.name.isNullOrBlank()
            && videoId.isNullOrBlank()
            && input.description.isNullOrBlank()
            && input.images.isNullOrEmpty()
            && input.artists == null
            && input.releases == null
            && input.works == null) {
            throw AppError.BadRequest.default(msg = "参数不能全为空")
        }
        val video = findExistsAndOwnOrThrow(id, user)

        //检查状态
        if (video.status != DocumentStatus.DRAFT) {
            throw AppError.BadRequest.default(msg = "该视频不在草稿状态")
        }
        if (video.subStatus == DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS) {
            throw AppError.BadRequest.default(msg = "待审核对象在转码无法编辑")
        }

        //覆盖视频的操作
        val oldVideoId = video.videoId
        if (videoId != null && videoId != oldVideoId) {
            video.videoId = videoId
            video.size = 0
            video.images = arrayOf()
            video.duration = 0
            video.videoStatus = ""
            val persist = save(video)
            videoVodService.refreshUploadStatusWithoutPublish(persist.id)
        }

        //基本信息的编辑
        input.name?.also { video.name = it }
        input.description?.also { video.description = it }
        input.images?.also { video.images = it }
        input.fileName?.also { video.fileName = it }
        input.artists?.also {
            video.artists.clear()
            video.artists.addAll(it.map(artistService::findExistsOrThrow))
        }
        input.works?.also {
            video.works.clear()
            video.works.addAll(it.map(workService::findExistsOrThrow))
        }
        input.releases?.also {
            video.releases.clear()
            video.releases.addAll(it.map(releaseService::findExistsOrThrow))
        }
        save(video)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateWorks(user: User, id: UUID, works: List<Work>) {
        updateWorks(id, works)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateReleases(user: User, id: UUID, releases: List<Release>) {
        updateReleases(id, releases)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateArtists(user: User, id: UUID, artists: List<Artist>) {
        updateArtists(id, artists)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateDescription(user: User, id: UUID, description: String) {
        updateDescription(id, description)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateWeight(user: User, id: UUID, commendLevel: Int) {
        updateWeight(id, commendLevel)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateName(user: User, id: UUID, name: String) {
        updateName(id, name)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateImages(user: User, id: UUID, images: Array<String>) {
        updateImages(id, images)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateAttribute(user: User, id: UUID, input: UpdateAttributeOutput) {
        updateAttribute(id, input)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun deleteAndNotify(user: User, id: UUID, reason: String) {
        deleteAndNotify(id, reason)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun softDelete(user: User, id: UUID) {
        softDelete(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun suppress(user: User, id: UUID) {
        suppress(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun publish(user: User, id: UUID) {
        publish(id)
    }

    @Locked("%{#user.id}-%{#id}")
    override fun updateSendTime(id: UUID, input: ChangeSendTimeInput, user: User) {
        val video = findExistsOrThrow(id)
        if (video.status != DocumentStatus.WAITING) {
            throw AppError.BadRequest.default(msg = "视频不处于待发布状态,不可以修改发布时间")
        }
        if (input.expectSendTime == null) {
            video.expectSendTime = ZonedDateTime.now()
            video.isSend = true
        } else {
            val expectSendTime = quartzService.transFromStringToZonedDateTime(input.expectSendTime!!)
            video.expectSendTime = expectSendTime
            video.timeString = input.expectSendTime!!
            video.isSend = false
        }
        save(video)
        /*删除原定时发布任务,新建新定时发布任务*/
        quartzService.removeJob("onTimingPublishVideo.${video.id}")
        eventPublisher.publishEvent(TimingPublishVideoEvent(video.id))
    }

    override fun deleteAndNotify(id: UUID, reason: String) {
        val video = findExistsOrThrow(id)
        video.fromAdmin.also {
            if (it) {
                throw AppError.BadRequest.default(msg = "只能删除用户发布的视频")
            }
        }
        softDelete(video)
        /*同时删除动态*/
        eventPublisher.publishEvent(DeleteTimeLineEvent(video.id))
        val event = VideoNotificationEvent(
            source = video,
            targetId = video.id,
            targetType = ModelEnum.Video,
            eventType = VideoEventType.DELETE,
            extra = reason,
            createdBy = video.createdBy!!
        )
        applicationEventPublisher.publishEvent(event)
    }

    /**
     * #1002322 取消发布前台用户的内容逻辑优化
     *
     * <p>个人中心草稿箱需要过滤被后台撤销发布的草稿
     * 状态{@code DocumentSubStatus@DRAFT_ADMIN_SUPPRESS}
     *
     * @since 2019年7月25日, AM 11:33:32
     */
    override fun drafts(user: User, q: String?, page: Pageable): Page<Video> {
        var expression = qVideo.deleted.isFalse
            .and(qVideo.status.eq(DocumentStatus.DRAFT))
            .and(qVideo.subStatus.ne(DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS))
            .and(qVideo.subStatus.ne(DocumentSubStatus.DRAFT_ADMIN_SUPPRESS)) //被后台下架的草稿，不对用户显示。
            .and(qVideo.published.isFalse)
            .and(qVideo.createdBy.eq(user))

        q?.also {
            expression = expression.and(qVideo.name.contains(it))
        }
        return findAll(expression, page)
    }

    override fun updateAttribute(id: UUID, input: WebUpdateAttributeOutput) {
        updateAttribute(findExistsOrThrow(id), input)
    }

    override fun updateAttribute(id: UUID, input: UpdateAttributeOutput) {
        updateAttribute(findExistsOrThrow(id), input)
    }

    override fun nextEsVideo(currentVideoId: UUID, sort: String?, key: String?): Video? {
        val cVideo = findExistsOrThrow(currentVideoId)
        return when (sort) {
            "-playCount" -> nextEsVideoByPlayCount(cVideo, key)
            "-createdAt" -> nextEsVideoByCreateAt(cVideo, key)
            "-weight" -> nextEsVideoByWeight(cVideo, key)
            else -> throw AppError.BadRequest.paramError("不支持的排序$sort")
        }
    }

    private fun nextEsVideoByWeight(cVideo: Video, key: String?): Video? {
        val queryBuilder = BoolQueryBuilder()
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        queryBuilder.filter(RangeQueryBuilder("weight").lt(cVideo.weight))
        key?.also {
            queryBuilder.must(QueryBuilders.matchQuery("name", key))
        }
        val searchQuery = NativeSearchQueryBuilder()
        searchQuery.withQuery(queryBuilder)
            .withPageable(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "weight")))
        val search = videoEsRepository.search(searchQuery.build())
        if (!search.isEmpty) {
            val first = search.first()
            return findExistsOrThrow(first.id)
        } else {
            return null
        }
    }

    private fun nextEsVideoByCreateAt(cVideo: Video, key: String?): Video? {
        val queryBuilder = BoolQueryBuilder()
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        queryBuilder.filter(
            RangeQueryBuilder("createdAt").format("yyyyMMddHHmmss")
                .lt(SimpleDateFormat("yyyyMMddHHmmss").format(cVideo.createdAt.minusHours(@Suppress("MagicNumber") 8).toDate())))
        key?.also {
            queryBuilder.must(QueryBuilders.matchQuery("name", key))
        }
        val searchQuery = NativeSearchQueryBuilder()
        searchQuery.withQuery(queryBuilder)
            .withPageable(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")))
        val search = videoEsRepository.search(searchQuery.build())
        if (!search.isEmpty) {
            val first = search.first()
            return findExistsOrThrow(first.id)
        } else {
            return null
        }
    }

    private fun nextEsVideoByPlayCount(cVideo: Video, key: String?): Video? {
        val queryBuilder = BoolQueryBuilder()
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        queryBuilder.filter(RangeQueryBuilder("playCount").lt(cVideo.playCount))
        key?.also {
            queryBuilder.must(QueryBuilders.matchQuery("name", key))
        }
        val searchQuery = NativeSearchQueryBuilder()
        searchQuery.withQuery(queryBuilder)
            .withPageable(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "playCount")))
        val search = videoEsRepository.search(searchQuery.build())
        if (!search.isEmpty) {
            val first = search.first()
            return findExistsOrThrow(first.id)
        } else {
            return null
        }
    }

    override fun otherList(user: User, input: PageQuery): Page<Video> {
        val expression = qVideo.createdBy.eq(user)
            .and(qVideo.deleted.isFalse)
            .and(qVideo.published.isTrue)
            .and(qVideo.status.eq(DocumentStatus.PUBLISHED))
        return findAll(expression, input.defaultSortByCreateAt())
    }

    override fun personalList(user: User, q: String?, page: PageQuery): Page<Video> {
        var expression = qVideo.createdBy.eq(user)
            .and(qVideo.deleted.isFalse)
            .and(qVideo.status.eq(DocumentStatus.PUBLISHED).and(qVideo.published.isTrue)
                .or(qVideo.status.eq(DocumentStatus.CHECKING))
                .or(qVideo.status.eq(DocumentStatus.DRAFT).and(qVideo.subStatus.eq(DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS))
                )
            )
        q?.also {
            expression = expression.and(qVideo.name.contains(q))
        }
        return findAll(expression, page.defaultSortByCreateAt())
    }

    val qVideo = QVideo.video

    override fun findByRelease(id: UUID): List<Video> {
        val release = releaseService.findByDeletedFalseAndId(id)
        return if (release != null) {
            val expression = qVideo.releases.contains(release)
                .and(qVideo.status.eq(DocumentStatus.PUBLISHED))
                .and(qVideo.published.isTrue)
                .and(qVideo.deleted.isFalse)
            findAll(expression).toList()
        } else {
            emptyList()
        }
    }

    override fun findByArtist(id: UUID): List<Video> {
        val artist = artistService.findByDeletedFalseAndId(id)
        return if (artist != null) {
            val expression = qVideo.artists.contains(artist)
                .and(qVideo.status.eq(DocumentStatus.PUBLISHED))
                .and(qVideo.published.isTrue)
                .and(qVideo.deleted.isFalse)
            findAll(expression).toList()
        } else {
            emptyList()
        }
    }

    override fun findByWork(id: UUID): List<Video> {
        val work = workService.findByDeletedFalseAndId(id)
        return if (work != null) {
            val expression = qVideo.works.contains(work)
                .and(qVideo.status.eq(DocumentStatus.PUBLISHED))
                .and(qVideo.published.isTrue)
                .and(qVideo.deleted.isFalse)
            findAll(expression).toList()
        } else {
            emptyList()
        }
    }

    private val logger = LoggerFactory.getLogger(VideoServiceImpl::class.java)

    override fun indexToEs(video: Video) {
        logger.debug("Index Video[${video.id}] to elasticsearch")
        esIndexService.indexToEs(video, videoMapper, videoEsRepository)
    }

    override fun indexToEs(videos: List<Video>) {
        esIndexService.indexToEs(
            videos.map(Video::id), videoMapper, videoRepository, videoEsRepository
        )
    }

    override fun asyncIndexToEs(videos: List<Video>): CompletableFuture<Unit> {
        return esIndexService.asyncIndexToEs(
            videos.map(Video::id), videoMapper, videoRepository, videoEsRepository
        )
    }

    override fun publish(id: UUID) {
        val e = findExistsOrThrow(id)
        e.publish()
        if (!e.isSend) {
            e.status = DocumentStatus.WAITING
            e.subStatus = DocumentSubStatus.WAITING_PUBLISH_CHECK_PASS
        }
        if (!e.fromAdmin && e.subStatus == DocumentSubStatus.DRAFT_ADMIN_SUPPRESS) {
            // 如果是用户发布的视频，则要修改状态。
            e.subStatus = DocumentSubStatus.PUBLISHED_CHECK_PASS
            eventPublisher.publishEvent(TimeLineEvent(ModelEnum.Video, e.id, e.createdBy!!))
        }
        val saved = save(e)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun suppress(id: UUID) {
        val video = findExistsOrThrow(id)
        /*如果被定时发送的视频被撤销发布,则删除定时发布任务*/
        if (video.status == DocumentStatus.WAITING) {
            quartzService.removeJob("onTimingPublishVideo.${video.id}")
        }
        video.suppress()
        if (!video.fromAdmin && video.subStatus == DocumentSubStatus.PUBLISHED_CHECK_PASS) {
            // 如果是用户发布的视频，则要修改状态。
            video.subStatus = DocumentSubStatus.DRAFT_ADMIN_SUPPRESS
            /*同时删除动态*/
            eventPublisher.publishEvent(DeleteTimeLineEvent(video.id))
            /*同步到ES*/
            val event = VideoNotificationEvent(
                source = video,
                targetId = video.id,
                targetType = ModelEnum.Video,
                eventType = VideoEventType.SUPPRESS,
                extra = null,
                createdBy = video.createdBy!!
            )
            applicationEventPublisher.publishEvent(event)
        }
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun refreshCollectCount(video: Video, count: Long) {
        video.collectCount = count
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun increasePlayCount(video: Video) {
        //FIXME 存在并发问题
        video.playCount++
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun increasePlayCountAndStartQuartz(video: Video) {
        increasePlayCount(video)
        //注入视频播放次数自动增长定时任务
        eventPublisher.publishEvent(VirtualPlayDataEvent(video.id))
    }

    override fun refreshShareCount(video: Video, count: Long) {
        video.shareCount = count
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun refreshLikeCount(video: Video, count: Long) {
        video.likeCount = count
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    override fun refreshCommentCount(video: Video, count: Long) {
        video.commentCount = count
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    override fun updateWeight(id: UUID, commendLevel: Int) {
        val video = findExistsOrThrow(id)
        video.weight = commendLevel
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateAttribute(video: Video, input: WebUpdateAttributeOutput) {
        video.also {
            it.url = input.url
            it.name = input.name
            it.description = input.description
        }
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateAttribute(video: Video, input: UpdateAttributeOutput) {
        video.also {
            it.url = input.url
            it.name = input.name
            it.description = input.description
            it.weight = input.weight
            it.labels.clear()
            it.labels.addAll(input.labels)
        }
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateFileName(video: Video, it: String) {
        video.fileName = it
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateName(video: Video, name: String) {
        video.name = name
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateURL(video: Video, url: String) {
        video.url = url
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateImages(video: Video, images: Array<String>) {
        video.images = images
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateDescription(video: Video, description: String) {
        video.description = description
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateArtists(video: Video, artists: List<Artist>) {
        video.artists.clear()
        video.artists.addAll(artists)
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateReleases(video: Video, releases: List<Release>) {
        video.releases.clear()
        video.releases.addAll(releases)
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    private fun updateWorks(video: Video, works: List<Work>) {
        video.works.clear()
        video.works.addAll(works)
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }


    override fun softDelete(entity: Video) {
        entity.softDelete()
        if (entity.status == DocumentStatus.WAITING) {
            quartzService.removeJob("onTimingPublishVideo.${entity.id}")
        }
        val saved = save(entity)
        eventPublisher.publishEvent(EntityEvent.softDeleted(saved))
    }

    override fun softDelete(id: UUID) {
        val video = findExistsOrThrow(id)
        softDelete(video)
    }

    override fun list(input: SearchInput): Page<Video> {
        val searchQuery = NativeSearchQueryBuilder()
        val booleanBuilder = BoolQueryBuilder()
        val status = input.status
        if (status != null) {
            booleanBuilder.must(
                QueryBuilders.termsQuery("status", status.name)
            )
        }
        if (input.name != null) {
            booleanBuilder.must(
                //TODO 待优化，此处只能根据单词搜索，后期需要模糊查询
                QueryBuilders.matchQuery("name", input.name)
            )
        }
        searchQuery.withQuery(booleanBuilder)
            .withPageable(input.pageable())
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
            .withSortings(input.sortings)

        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsVideo::class.java)
            .transform(this)
    }

    override fun listForBackend(input: VideoListInput): Page<Video> {
        val createdName = input.createdBy
        val name = input.name
        val status = input.status

        val queryBuilder = BooleanBuilder(qVideo.deleted.isFalse)
        name?.also {
            queryBuilder.and(qVideo.name.contains(it))
        }
        createdName?.also {
            queryBuilder.and(qVideo.createdBy.nickName.contains(it))
        }

        if (status != null && status == DocumentStatus.PUBLISHED) {
            queryBuilder.and(qVideo.status.eq(DocumentStatus.PUBLISHED))
        } else if (status != null && status == DocumentStatus.DRAFT) {
            queryBuilder.and(qVideo.status.eq(DocumentStatus.DRAFT))
                .and(qVideo.subStatus.notIn((listOf(
                    DocumentSubStatus.DRAFT_USER_NEW,
                    DocumentSubStatus.DRAFT_USER_DUP,
                    DocumentSubStatus.DRAFT_USER_EDIT,
                    DocumentSubStatus.DRAFT_ADMIN_REFUSE,
                    DocumentSubStatus.DRAFT_USER_REVOKE,
                    DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL,
                    DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS
                ))))
        } else if (status != null && status == DocumentStatus.WAITING) {
            queryBuilder.and(qVideo.status.eq(DocumentStatus.WAITING))
        } else {
            queryBuilder.and(qVideo.status.`in`(listOf(
                DocumentStatus.DRAFT,
                DocumentStatus.PUBLISHED,
                DocumentStatus.WAITING
            )))
                .and(qVideo.subStatus.notIn(listOf(
                    DocumentSubStatus.DRAFT_USER_NEW,
                    DocumentSubStatus.DRAFT_USER_DUP,
                    DocumentSubStatus.DRAFT_USER_EDIT,
                    DocumentSubStatus.DRAFT_ADMIN_REFUSE,
                    DocumentSubStatus.DRAFT_USER_REVOKE,
                    DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL,
                    DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS
                )).or(qVideo.subStatus.isNull))
        }

        return findAll(queryBuilder, input.defaultSortByCreateAt())
    }

    override fun listForBackend(input: SearchInput): Page<Video> {
        val searchQuery = NativeSearchQueryBuilder()
        val booleanBuilder = BoolQueryBuilder()
        val status = input.status
        if (status != null && status == DocumentStatus.PUBLISHED) {
            booleanBuilder.must(
                QueryBuilders.termsQuery("status", status.name)
            )
        } else if (status != null && status == DocumentStatus.DRAFT) {
            booleanBuilder
                .should(
                    BoolQueryBuilder()
                        .must(QueryBuilders.termsQuery("status", status.name))
                        .must(QueryBuilders.termsQuery("fromAdmin", true))
                )
                .should(
                    BoolQueryBuilder()
                        .must(QueryBuilders.termsQuery("status", status.name))
                        .must(QueryBuilders.termsQuery("fromAdmin", false))
                        .must(QueryBuilders.termsQuery(
                            "subStatus",
                            listOf(
                                DocumentSubStatus.PUBLISHED_CHECK_PASS.name,
                                DocumentSubStatus.DRAFT_ADMIN_SUPPRESS.name
                            )
                        )
                        )
                )
        } else {
            booleanBuilder
                .should(
                    BoolQueryBuilder().must(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
                )
                .should(
                    BoolQueryBuilder()
                        .must(QueryBuilders.termsQuery("status", DocumentStatus.DRAFT.name))
                        .must(QueryBuilders.termsQuery("fromAdmin", true))
                )
                .should(
                    BoolQueryBuilder()
                        .must(QueryBuilders.termsQuery("status", DocumentStatus.DRAFT.name))
                        .must(QueryBuilders.termsQuery("fromAdmin", false))
                        .must(QueryBuilders.termsQuery(
                            "subStatus",
                            listOf(
                                DocumentSubStatus.PUBLISHED_CHECK_PASS.name,
                                DocumentSubStatus.DRAFT_ADMIN_SUPPRESS.name
                            )
                        )
                        )
                )
        }
        if (input.name != null) {
            booleanBuilder.must(
                //TODO 待优化，此处只能根据单词搜索，后期需要模糊查询
                QueryBuilders.matchQuery("name", input.name)
            )
        }
        searchQuery.withFilter(booleanBuilder)
            .withPageable(input.pageable())
            .withSourceFilter(FetchSourceFilterBuilder().withIncludes("id").build())
            .withSortings(input.sortings)

        return elasticsearchTemplate.queryForPage(searchQuery.build(), EsVideo::class.java)
            .transform(this)
    }

    override fun completionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("video",
            listOf("nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        return CompletionSuggestResult("video", options)
    }

    override fun backendCompleteionSuggest(word: String): CompletionSuggestResult {
        val options = searchCompletionSuggestTemplate("video",
            listOf("nameCompletion"),
            word,
            elasticsearchTemplate = elasticsearchTemplate)
        val option = KeyOption("name", options.map { it.options }.flatten().distinct())
        return CompletionSuggestResult("video", listOf(option))
    }

    override fun updateName(id: UUID, name: String) {
        findExistsOrThrow(id).also {
            updateName(it, name)
        }
    }

    override fun updateURL(id: UUID, url: String) {
        findExistsOrThrow(id).also {
            updateURL(it, url)
        }
    }

    override fun updateImages(id: UUID, images: Array<String>) {
        findExistsOrThrow(id).also {
            updateImages(it, images)
        }
    }

    override fun updateDescription(id: UUID, description: String) {
        findExistsOrThrow(id).also {

        }
    }

    override fun updateArtists(id: UUID, artists: List<Artist>) {
        findExistsOrThrow(id).also {
            it.artists.clear()
            it.artists.addAll(artists)
            val saved = save(it)
            eventPublisher.publishEvent(EntityEvent.updated(saved))
        }
    }

    override fun updateReleases(id: UUID, releases: List<Release>) {
        findExistsOrThrow(id).also {
            it.releases.clear()
            it.releases.addAll(releases)
            val saved = save(it)
            eventPublisher.publishEvent(EntityEvent.updated(saved))
        }
    }

    override fun updateWorks(id: UUID, works: List<Work>) {
        findExistsOrThrow(id).also {
            it.works.clear()
            it.works.addAll(works)
            val saved = save(it)
            eventPublisher.publishEvent(EntityEvent.updated(saved))
        }
    }

    override fun afterPublishedBeDelete(target: Video) {
        /*同时删除动态*/
        eventPublisher.publishEvent(DeleteTimeLineEvent(target.id))
        eventPublisher.publishEvent(EntityEvent.softDeleted(target))
    }
}


@Service("videoVodService")
@Transactional
class VideoVodServiceImpl(
    @Lazy @Autowired val videoService: VideoService,
    @Autowired val vodConfig: VodConfig,
    @Autowired val videoCheckService: CheckService,
    @Autowired val appPushService: AppPushService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val applicationEventPublisher: ApplicationEventPublisher
) : VideoVodService, VideoService by videoService {

    val logger: Logger = LoggerFactory.getLogger(VideoVodServiceImpl::class.java)

    override fun refreshUploadStatus(id: UUID): String {
        val video = findExistsOrThrow(id)
        val videoId = video.videoId ?: throw AppError.BadRequest.default(msg = "视频未绑定videoId")
        if (video.isNormal) {
            return "Normal"
        }
        return refreshVideoInfo(videoId, video)
    }

    override fun refreshUploadStatusWithoutPublish(id: UUID): String {
        val video = findExistsOrThrow(id)
        val videoId = video.videoId ?: throw AppError.BadRequest.default(msg = "视频未绑定videoId")
        if (video.isNormal) {
            return "Normal"
        }
        return refreshVideoInfoWithoutPublish(videoId, video)
    }

    override fun updateVodInfo(video: Video, videoId: String, title: String, fileName: String) {
        // 清空所有Vod信息，并重新绑定videoId
        video.videoId = videoId
        video.name = title
        video.fileName = fileName
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    /**
     * 刷新上传凭证
     */
    override fun refreshUploadVideo(videoId: String): RefreshUploadVideoResponse {
        val client = initVodClient()
        val request = RefreshUploadVideoRequest()
        request.videoId = videoId
        return client.getAcsResponse(request)
    }

    /**
     * 转码完成成立事件
     */
    override fun transCodeCompleteHandler(transCodeComplete: TransCodeComplete) {
        logger.trace("In to TransCodeCompleteHandler, Get TransCodeComplete: $transCodeComplete")
        val videoId = transCodeComplete.VideoId
        logger.trace("And find link video that id: $videoId")
        val video = findByVideoId(videoId)
        if (video == null) {
            logger.error("Handle transCode-event error, reason: Not found a video that VideoId=$videoId")
            logger.trace("TransCodeCompleteHandler quit by error.")
            return
        }
        if (transCodeComplete.Status == "success") {
            logger.trace("This video transCode success")
            refreshVideoInfo(videoId, video)
        }
        logger.trace("Latest video: $video.")
        logger.trace("TransCodeCompleteHandler quit.")
    }

    /**
     * 转码完成成立事件, 转码完成不直接发布
     */
    override fun transCodeCompleteHandlerWithoutPublish(transCodeComplete: TransCodeComplete) {
        logger.debug("In to TransCodeCompleteHandler, Get TransCodeComplete: $transCodeComplete")
        val videoId = transCodeComplete.VideoId
        logger.debug("And find link video that id: $videoId")
        val video = findByVideoId(videoId)
        if (video == null) {
            logger.error("Handle transCode-event error, reason: Not found a video that VideoId=$videoId")
            logger.debug("TransCodeCompleteHandler quit by error.")
            return
        }
        if (transCodeComplete.Status == "success") {
            logger.debug("This video transCode success")
            refreshVideoInfoWithoutPublish(videoId, video)
            if (video.isNormal) {
                //如果subStatus是DRAFT_VIDEO_CHECKING_WAIT_TRANS, 则在转码成功后发起审核提交。
                if (video.subStatus == DocumentSubStatus.DRAFT_VIDEO_CHECKING_WAIT_TRANS) {
                    videoCheckService.commit(video, video.createdBy!!, null)
                }
            }
        } else {
            video.videoStatus = "TranscodeFail"
            video.status = DocumentStatus.DRAFT
            video.subStatus = DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL
            appPushService.sendVideoStatusChangeUnicast(video)
            //转码失败通知
            val event = VideoNotificationEvent(
                source = video,
                targetId = video.id,
                targetType = ModelEnum.Video,
                eventType = VideoEventType.TRANS_FAIL,
                createdBy = video.createdBy!!
            )
            applicationEventPublisher.publishEvent(event)
            val saved = save(video)
            eventPublisher.publishEvent(EntityEvent.updated(saved))
        }
        logger.debug("Latest video: $video.")
        logger.debug("TransCodeCompleteHandler quit.")
    }

    private fun refreshVideoInfoWithoutPublish(videoId: String, video: Video): String {
        if (video.isNormal) {
            return "Normal"
        }
        val vodVideo = getVideoInfo(videoId).video
        val status = vodVideo.status
        logger.trace("""
                    Fetch vod-video info:
                    - status: $status
                    - duration: ${vodVideo.duration}
                    - coverURL: ${vodVideo.coverURL}
                    - size: ${vodVideo.size}
                    - snapshots: ${vodVideo.snapshots}
                """.trimIndent())

        //更新视频信息
        video.videoStatus = status
        if (status == "Normal") {
            //上传完成
            video.duration = vodVideo.duration.toLong()
            video.size = vodVideo.size
            logger.trace("Upload success and refresh video: $video")
        }
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
        return status
    }

    private fun refreshVideoInfo(videoId: String, video: Video): String {
        if (video.isNormal) {
            return "Normal"
        }
        val vodVideo = getVideoInfo(videoId).video
        val status = vodVideo.status
        logger.trace("""
                    Fetch vod-video info:
                    - status: $status
                    - duration: ${vodVideo.duration}
                    - coverURL: ${vodVideo.coverURL}
                    - size: ${vodVideo.size}
                    - snapshots: ${vodVideo.snapshots}
                """.trimIndent())

        //更新视频信息
        video.videoStatus = status
        if (status == "Normal") {
            //上传完成
            video.duration = vodVideo.duration.toLong()
            video.size = vodVideo.size
            logger.trace("Upload success and refresh video: $video")
            //转码成功后，直接发布
            video.publish()
        }
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
        return status
    }

    /**
     * 文件上传成功处理事件
     */
    override fun fileUploadCompleteHandler(uploadComplete: UploadComplete) {
        logger.trace("In to FileUploadCompleteHandler ...")
        val videoId = uploadComplete.VideoId
        logger.trace("find link video that id: $videoId")
        val video = findByVideoId(videoId)
        if (video == null) {
            logger.error("Not found a video that VideoId=$videoId")
            logger.trace("FileUploadCompleteHandler quit by error.")
            return
        }
        video.videoStatus = "UploadSucc"
        val saved = save(video)
        eventPublisher.publishEvent(EntityEvent.updated(saved))
        logger.trace("FileUploadCompleteHandler quit.")
    }

    override fun getVideoInfo(videoId: String): GetVideoInfoResponse {
        val request = GetVideoInfoRequest()
        request.videoId = videoId
        return initVodClient().getAcsResponse(request)
    }

    override fun getVideoPlayInfo(videoId: String): GetPlayInfoResponse {
        val request = GetPlayInfoRequest()
        request.authTimeout = vodConfig.play.authTimeout
        request.outputType = vodConfig.play.outputType
        request.videoId = videoId
        return initVodClient().getAcsResponse(request)
    }

    /**
     * AccessKeyId/AccessKeySecret
     */
    override fun vodACKConfigure(): Pair<String, String> {
        val key = vodConfig.key
        val secret = vodConfig.secret
        if (key == null || secret == null) {
            logger.error("Please config key/secret before use VideoVodService.")
            throw VideoException(msg = "Please config key/secret before use VideoVodService.")
        }
        return key to secret
    }

    /**
     * 点播服务接入区域
     * https://help.aliyun.com/document_detail/98194.html?spm=a2c4g.11186623.2.18.133b3bd9yaK1qL
     */
    override fun vodRegionId(): String {
        return vodConfig.regionId
    }


    /**
     * 分类ID
     */
    fun vodCateId(): Long {
        val cateId = vodConfig.cateId
        if (cateId == null) {
            logger.debug("上传视频未设置分类。")
            return 0
        }
        return cateId
    }

    /**
     * 消息回调设置，指定时以此为准，否则以 全局设置的事件通知 为准。
     * 取值示例：{"CallbackType":"http", "CallbackURL":"http://callback-host/addr"}
     */
    override fun vodCallbackURL(): String {
        val url = vodConfig.callback.url
        if (url == null) {
            logger.warn("VideoVodService vod.callback.url is not define!")
            return ""
        }
        return url
    }

    /**
     * 其中 CallbackType为回调方式，默认为http，CallbackURL为回调地址
     */
    override fun vodCallbackType(): String {
        return vodConfig.callback.type
    }

    /**
     * 转码模板组ID。
     * 当不为空时，会使用该指定的模板组进行转码。可在 点播控制台 > 转码设置 里查看模版组ID。
     */
    override fun vodTemplateGroupId(): String {
        val templateGroupId = vodConfig.templateGroupId
        if (templateGroupId == null) {
            logger.debug("VideoVodService vod.templateGroupId is not define! 上传视频将不会直接转码")
            return ""
        }
        return templateGroupId
    }

    /**
     * <em>初始化</em>
     * 点播服务的接入区域参考 接入区域标识，如国内请使用 cn-shanghai。
     */
    @Throws(ClientException::class)
    fun initVodClient(): DefaultAcsClient {
        val regionId = vodRegionId()  // 点播服务接入区域
        val profile = DefaultProfile.getProfile(regionId, vodACKConfigure().first, vodACKConfigure().second)
        return DefaultAcsClient(profile)
    }

    /**
     * 获取视频上传地址和凭证
     * client 发送请求客户端
     * @return CreateUploadVideoResponse 获取视频上传地址和凭证响应数据
     * @throws Exception
     */
    override fun createUploadVideo(title: String, fileName: String): CreateUploadVideoResponse {
        val request = CreateUploadVideoRequest().also {
            it.title = title
            it.fileName = fileName
            it.templateGroupId = vodTemplateGroupId()
            it.cateId = vodCateId()
        }
        val userData = JSONObject()
        val messageCallback = JSONObject()
        messageCallback["CallbackURL"] = vodCallbackURL()
        messageCallback["CallbackType"] = vodCallbackType()
        userData["MessageCallback"] = messageCallback.toJSONString()
        request.userData = userData.toJSONString()
        logger.debug("CreateUploadVideoRequest(${request.title},${request.fileName},${request.userData})")
        return initVodClient().getAcsResponse(request)
    }
}
