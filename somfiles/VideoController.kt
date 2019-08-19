package com.musicbible.controller

import com.aliyun.oss.OSSClient
import com.aliyun.oss.OSSException
import com.aliyuncs.exceptions.ClientException
import com.aliyuncs.vod.model.v20170321.CreateUploadVideoResponse
import com.aliyuncs.vod.model.v20170321.GetVideoInfoResponse
import com.aliyuncs.vod.model.v20170321.RefreshUploadVideoResponse
import com.boostfield.aliossspringbootstarter.AliOSSService
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.google.gson.Gson
import com.musicbible.event.EntityEvent
import com.musicbible.mapper.video.ChangeSendTimeInput
import com.musicbible.mapper.video.UpdateAssociationOutput
import com.musicbible.mapper.video.UpdateAttributeOutput
import com.musicbible.mapper.video.UpdateDescriptionOutput
import com.musicbible.mapper.video.UpdateImagesOutput
import com.musicbible.mapper.video.UpdateNameInput
import com.musicbible.mapper.video.UpdateWeightInput
import com.musicbible.mapper.video.VideoDetailOutput
import com.musicbible.mapper.video.VideoListInput
import com.musicbible.mapper.video.VideoListOutput
import com.musicbible.mapper.video.VideoMapper
import com.musicbible.mapper.vod.Event
import com.musicbible.mapper.vod.TransCodeComplete
import com.musicbible.mapper.vod.UploadComplete
import com.musicbible.model.User
import com.musicbible.model.Video
import com.musicbible.security.UserOrThrow
import com.musicbible.service.ArtistService
import com.musicbible.service.ReleaseService
import com.musicbible.service.UserService
import com.musicbible.service.VideoService
import com.musicbible.service.VideoVodService
import com.musicbible.service.WorkService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.annotations.ApiIgnore
import java.net.URL
import java.util.UUID
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/v0/video")
@Api(value = "/api/v0/video", tags = ["S 视频"], description = "Video")
class VideoController(
    @Autowired val videoService: VideoService,
    @Autowired val videoMapper: VideoMapper,
    @Autowired val releaseService: ReleaseService,
    @Autowired val workService: WorkService,
    @Autowired val artistService: ArtistService,
    @Autowired val videoVodService: VideoVodService,
    @Autowired val aliOSSService: AliOSSService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val userService: UserService
) {

    private val logger = LoggerFactory.getLogger(VideoController::class.java)

    @ApiOperation(
        value = "封面转存",
        notes = """
            当使用VOD私有桶时，VOD提供的封面链接存在时效，会有过期失效的问题。
            所以将VOD提供的截图封面URL，存到OSS服务中，返回ObjectKey，以便持久可访问性。
            并且，转存成功后，ObjectKey会直接保存至该id下的视频封面中。
        """)
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @PutMapping("/{id}/cover/oss")
    fun convertVodCoverToOSSAndSave(
        @PathVariable id: UUID,
        @RequestParam bucketName: String,
        @RequestParam downloadURL: String,
        @RequestParam objectKey: String
    ) {
        val endpoint = "http://oss-cn-hangzhou.aliyuncs.com"
        val accessKeyId = aliOSSService.aliOSSProperties.accessKey
        val accessKeySecret = aliOSSService.aliOSSProperties.accessSecret
        @Suppress("DEPRECATION")
        val ossClient = OSSClient(endpoint, accessKeyId, accessKeySecret)
        try {
            val inputStream = URL(downloadURL).openStream()
            ossClient.putObject(bucketName, objectKey, inputStream)
            videoService.updateImages(id, arrayOf(objectKey))
        } catch (e: OSSException) {
            throw AppError.ServiceUnavailable.default(msg = "封面转存调用失败")
        } catch (e: ClientException) {
            throw AppError.ServiceUnavailable.default(msg = "封面转存调用失败")
        } finally {
            ossClient.shutdown()
        }
    }

    @ApiOperation(value = "判断是否已经绑定videoId",
        notes = "true: 已绑定 false:未绑定")
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @GetMapping("/{id}/bind/video_id")
    fun hadBindVideoId(
        @PathVariable id: UUID): Boolean {
        val video = videoService.findExistsOrThrow(id)
        return video.videoId != null
    }

    @ApiOperation(value = "获取播放列表",
        notes = "文档：https://help.aliyun.com/document_detail/56124.html?spm=a2c4g.11186623.6.710.59927344C702g4")
    @PreAuthorize("hasAuthority('READ_VIDEO')")
    @GetMapping("/{id}/playInfo")
    fun getPlayInfo(@PathVariable id: UUID): com.aliyuncs.vod.model.v20170321.GetPlayInfoResponse {
        val video = videoVodService.findExistsOrThrow(id)
        val videoId = video.videoId
        if (videoId != null) {
            return videoVodService.getVideoPlayInfo(videoId)
        } else {
            throw AppError.BadRequest.default(msg = "未绑定videoId的视频")
        }
    }

    @ApiOperation(value = "获取VOD视频详情, 包含截图",
        notes = "请在转码成功后获取, 获取的数据说明：https://help.aliyun.com/document_detail/52835.html?spm=a2c4g.11186623.6.714.ec596bd1jVN491")
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @GetMapping("/{id}/videoInfo")
    fun videoInfo(
        @PathVariable id: UUID): GetVideoInfoResponse {
        val video = videoService.findExistsOrThrow(id)
        val videoId = video.videoId
        if (videoId != null) {
            if (video.isNormal)
                return videoVodService.getVideoInfo(videoId)
        }
        throw AppError.BadRequest.default(msg = "转码成功后才能获取视频信息")
    }

    @ApiOperation(value = "刷新当前的视频上传状态，如遇到网络延迟，显示上传中，实际已经上传成功。",
        notes = """
            -> https://help.aliyun.com/document_detail/52839.html?spm=a2c4g.11186623.2.16.6b157344LAXvFq#Video
            Uploading	上传中	视频的初始状态，表示正在上传。
            UploadFail	上传失败	由于是断点续传，无法确定上传是否失败，故暂不会出现此值。
            UploadSucc	上传完成	-
            Transcoding	转码中	-
            TranscodeFail	转码失败	转码失败，一般是由于原片存在问题。可在事件通知的 转码完成消息 获取ErrorMessage失败信息，或提交工单联系我们。
            Checking	审核中	在 视频点播控制台 > 全局设置 > 审核设置 开启了 先审后发，转码成功后视频状态会变成 审核中，此时视频只能在控制台播放。
            Blocked	    屏蔽	在审核时屏蔽视频。
            Normal  	正常
            null或空    上传失败或未上传
        """)
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @GetMapping("/{id}/upload_status/refresh")
    fun refreshUploadStatus(@PathVariable id: UUID): Map<String, String> {
        return mapOf("status" to videoVodService.refreshUploadStatus(id))
    }

    @ApiOperation(
        value = "获取上传地址和凭证",
        notes = """
            1. 第一次上传: 直接调用该接口通过视频id获取凭证，得到的videoId会和该视频绑定，且在replace=false的情况下，
            同一个id只能绑定一次videoId, 重复请求会报400。如果是因为凭证超时可以调用刷新凭证接口。
            对应一个videoId只能上传一次视频。
            2. 如果是非第一次上传，即已经上传过视频: 需要设置replace=true, 来覆盖之前的上传视频的所有相关信息。
            （注：该操作执行之后将无法恢复。）
        """
    )
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @GetMapping("/{id}/certificate")
    fun getUploadCertificate(
        @PathVariable id: UUID,
        @RequestParam title: String,
        @RequestParam fileName: String,
        @RequestParam replace: Boolean
    ): CreateUploadVideoResponse {
        //是否已有videoId, 则转到刷新凭证逻辑
        val video = videoService.findExistsOrThrow(id)
        if (!replace) {
            if (video.videoId != null) {
                throw AppError.BadRequest.default(msg = "该视频已绑定videoId, 请调用刷新凭证接口来获取新的凭证。")
            }
        }
        //如果扩展名是m2ts，则改为MTS
        val fName = m2tsToMTS(fileName)
        try {
            val createUploadVideo = videoVodService.createUploadVideo(title, fName)
            //获取到凭证后将videoId保存到Video
            videoVodService.updateVodInfo(video, createUploadVideo.videoId, title, fName)
            return createUploadVideo
        } catch (e: ClientException) {
            throw AppError.BadRequest.default(msg = e.errMsg)
        }
    }

    private fun m2tsToMTS(fileName: String): String {
        return fileName.replace("m2ts", "MTS")
    }

    @ApiOperation(value = "刷新凭证")
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @GetMapping("/{id}/certificate/refresh")
    fun refreshUploadCertificate(
        @PathVariable id: UUID): RefreshUploadVideoResponse {
        val video = videoService.findExistsOrThrow(id)
        val videoId = video.videoId ?: throw AppError.BadRequest.default(msg = "该视频未绑定videoId")
        try {
            return videoVodService.refreshUploadVideo(videoId)
        } catch (e: ClientException) {
            throw AppError.BadRequest.default(msg = e.message.orEmpty())
        }
    }

    @ApiOperation("新建空视频")
    @PreAuthorize("hasAuthority('CREATE_VIDEO')")
    @PostMapping
    fun create(): CreatedResponse {
        val video = videoService.save(Video().also {
            it.suppress()
            it.fromAdmin = true
        })
        eventPublisher.publishEvent(EntityEvent.created(video))
        return RestResponse.created(video)
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('READ_VIDEO')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): VideoDetailOutput {
        val video = videoService.findExistsOrThrow(id)
        return videoMapper.toDetail(video)
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('READ_VIDEO')")
    @GetMapping
    fun list(input: VideoListInput): PageResponse<VideoListOutput> {
        val list = videoService.listForBackend(input)
        val pages = list.map(videoMapper::toList)
        return RestResponse.page(pages)
    }

    @ApiOperation("发布")
    @PreAuthorize("hasAuthority('PUBLISH_VIDEO')")
    @PutMapping("/{id}/publish")
    fun publish(@UserOrThrow user: User, @PathVariable id: UUID) {
        videoService.publish(user, id)
    }

    @ApiOperation("撤销发布")
    @PreAuthorize("hasAuthority('PUBLISH_VIDEO')")
    @PutMapping("/{id}/suppress")
    fun suppress(@UserOrThrow user: User, @PathVariable id: UUID) {
        videoService.suppress(user, id)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('DELETE_VIDEO')")
    @DeleteMapping("/{id}")
    fun delete(@UserOrThrow user: User, @PathVariable id: UUID) {
        videoService.softDelete(user, id)
    }

    @ApiOperation("删除用户发布的视频")
    @PreAuthorize("hasAuthority('DELETE_VIDEO')")
    @DeleteMapping("/{id}/created_by_user")
    fun deleteVideoCreatedByUser(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestParam reason: String
    ) {
        videoService.deleteAndNotify(user, id, reason)
    }

    @ApiOperation("修改属性")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/attribute")
    fun updateAttribute(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateAttributeOutput
    ) {
        videoService.updateAttribute(user, id, input)
    }

    @ApiOperation("修改封面")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/image")
    fun updateImages(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateImagesOutput
    ) {
        videoService.updateImages(user, id, input.images)
    }

    @ApiOperation("修改名称")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/name")
    fun updateName(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateNameInput
    ) {
        videoService.updateName(user, id, input.name)
    }

    @ApiOperation("修改权重")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/weight")
    fun updateWeight(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateWeightInput
    ) {
        videoService.updateWeight(user, id, input.commendLevel)
    }

    @ApiOperation("修改描述")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/description")
    fun updateDescription(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateDescriptionOutput
    ) {
        videoService.updateDescription(user, id, input.description)
    }

    @ApiOperation("修改关联艺术家")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/artist")
    fun updateArtists(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateAssociationOutput
    ) {
        val artists = input.ids.toMutableSet().map(artistService::findExistsOrThrow)
        videoService.updateArtists(user, id, artists)
    }

    @ApiOperation("修改关联唱片")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/release")
    fun updateReleases(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateAssociationOutput
    ) {
        val releases = input.ids.toMutableSet().map(releaseService::findExistsOrThrow)
        videoService.updateReleases(user, id, releases)
    }

    @ApiOperation("修改关联作品")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/work")
    fun updateWorks(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: UpdateAssociationOutput
    ) {
        val works = input.ids.toMutableSet().map(workService::findExistsOrThrow)
        videoService.updateWorks(user, id, works)
    }

    @ApiOperation("修改定时发布视频发送时间")
    @PreAuthorize("hasAuthority('UPDATE_VIDEO')")
    @PutMapping("/{id}/sendTime")
    fun updateSendTime(@UserOrThrow user: User, @PathVariable id: UUID, @Validated @RequestBody input: ChangeSendTimeInput) {
            videoService.updateSendTime(id, input, user)
    }
}


@RestController
@RequestMapping("/api/v0/video/callback")
@ApiIgnore
class CallbackController(
    @Autowired val videoVodService: VideoVodService
) {
    val logger = LoggerFactory.getLogger(CallbackController::class.java)

    /**
     * 回调事件路由
     */
    @PostMapping
    fun callback(request: HttpServletRequest) {
        val body = IOUtils.toString(request.inputStream, request.characterEncoding)
        val event = parseBodyToEvent(body)
        logger.debug("Vod callback event:{$event｝,  body:{$body}")
        when (event.EventType) {
            "TranscodeComplete" -> transCodeComplete(body)
            "FileUploadComplete" -> fileUploadComplete(body)
            else -> logger.warn("No handler for this event! ${event.EventType}")
        }
    }

    fun parseBodyToEvent(body: String): Event {
        return Gson().fromJson(body, Event::class.java)
    }

    /**
     * 上传成功回调
     * https://help.aliyun.com/document_detail/55630.html?spm=a2c4g.11186623.6.623.4d004eafLKXDMf
     */
    fun fileUploadComplete(body: String) {
        val uploadComplete = Gson().fromJson(body, UploadComplete::class.java)
        videoVodService.fileUploadCompleteHandler(uploadComplete)
    }

    /**
     * 视频转码完成
     * https://help.aliyun.com/document_detail/55638.html?spm=a2c4g.11186623.6.626.63bf26beBUWhQy
     */
    fun transCodeComplete(body: String) {
        val transCodeComplete = Gson().fromJson(body, TransCodeComplete::class.java)
        videoVodService.transCodeCompleteHandler(transCodeComplete)
    }
}
