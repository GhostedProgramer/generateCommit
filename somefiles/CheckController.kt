package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.artist.ArtistMapper
import com.musicbible.mapper.check.CheckDetailOutput
import com.musicbible.mapper.check.CheckListInput
import com.musicbible.mapper.check.CheckListOutput
import com.musicbible.mapper.check.CheckMapper
import com.musicbible.mapper.check.CheckingInput
import com.musicbible.mapper.release.ReleaseMapper
import com.musicbible.mapper.video.VideoMapper
import com.musicbible.mapper.work.WorkMapper
import com.musicbible.model.CheckStatus
import com.musicbible.model.Document
import com.musicbible.model.ModelEnum
import com.musicbible.model.User
import com.musicbible.model.Video
import com.musicbible.repository.base.DocumentRepository
import com.musicbible.security.UserOrThrow
import com.musicbible.service.ArtistService
import com.musicbible.service.QuartzService
import com.musicbible.service.ReleaseService
import com.musicbible.service.RepositoryProvider
import com.musicbible.service.VideoService
import com.musicbible.service.WorkService
import com.musicbible.service.check.CheckService
import com.musicbible.service.check.CommonCheckService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime
import java.util.UUID


@RestController
@RequestMapping("/api/v0/check")
@Api(value = "/api/v0/check", tags = ["S 审核"], description = "Check")
class CheckController(
    @Autowired val checkService: CommonCheckService,
    @Autowired val videoCheckService: CheckService,
    @Autowired val checkMapper: CheckMapper,
    @Autowired val releaseService: ReleaseService,
    @Autowired val releaseMapper: ReleaseMapper,
    @Autowired val artistMapper: ArtistMapper,
    @Autowired val artistService: ArtistService,
    @Autowired val workMapper: WorkMapper,
    @Autowired val workService: WorkService,
    @Autowired val videoService: VideoService,
    @Autowired val videoMapper: VideoMapper,
    @Autowired val targetRepositoryProvider: RepositoryProvider<DocumentRepository<Document>>,
    @Autowired val quartzService: QuartzService
) : BaseController() {

    @PreAuthorize("hasAuthority('CHECK_VIDEO')")
    @ApiOperation(
        value = "列表",
        notes = """
            1. status参数只支持WAIT、REFUSE，默认不传参数下，表示WAIT和REFUSE状态的审核。
            2. type参数只支持Release、Artist、Work、Video，默认不传参数下，表示同时4个类型。
            3. 有'q'参数存在时，必须有qType表示类型，否则接口会400
        """
    )
    @GetMapping
    fun list(input: CheckListInput): PageResponse<CheckListOutput> {

        //校验type
        input.type?.also {
            if (!listOf(ModelEnum.Release, ModelEnum.Work, ModelEnum.Artist, ModelEnum.Video).contains(it)) {
                throw AppError.BadRequest.default(msg = "type参数不符合要求")
            }
        }

        //校验status
        input.status?.also {
            if (!listOf(CheckStatus.WAIT, CheckStatus.REFUSE).contains(it)) {
                throw AppError.BadRequest.default(msg = "status参数不符合要求")
            }
        }

        //检验q/qType
        val q = input.q
        val key = input.key
        if (!q.isNullOrBlank()) {
            if (key == null) {
                throw AppError.BadRequest.default(msg = "条件搜索缺少key")
            }
        }

        val map = checkService.list(input).map(checkMapper::toList)
        map.forEach {
            when (it.targetType) {
                ModelEnum.Release -> it.target = releaseService.findById(it.targetId!!).orElse(null)?.let(releaseMapper::toIdentity)
                ModelEnum.Work -> it.target = workService.findById(it.targetId!!).orElse(null)?.let(workMapper::toIdentity)
                ModelEnum.Artist -> it.target = artistService.findById(it.targetId!!).orElse(null)?.let(artistMapper::toIdentity)
                ModelEnum.Video -> it.target = videoService.findById(it.targetId!!).orElse(null)?.let(videoMapper::toName)
                else -> AppError.BadRequest.illegalOperate(msg = "未实现的映射")
            }
        }
        return map.let(RestResponse::page)
    }

    @PreAuthorize("hasAuthority('CHECK_VIDEO')")
    @ApiOperation(
        value = "审核详情",
        notes = """
            1. 此接口只返回审核结果、原因、提交说明
            2. 要获取release/video/artist/work相关详情，请调用各类型自己的detail接口。
        """
    )
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): CheckDetailOutput {
        return checkService.findById(id)
            .orElseThrow { AppError.NotFound.default(msg = "找不到该审核") }
            .let(checkMapper::toDetail)
    }

    @Transactional
    @PreAuthorize("hasAuthority('CHECK_VIDEO')")
    @ApiOperation(
        value = "审核视频(仅能审核视频)",
        notes = """
            1. result审核结果为false的时候，需要附加refusedReason参数。
        """
    )
    @PutMapping("/{id}")
    fun checkVideo(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: CheckingInput
    ) {
        val check = checkService.findById(id)
            .orElseThrow { AppError.NotFound.default(msg = "找不到该审核") }

        if (check.targetType != ModelEnum.Video) {
            throw AppError.BadRequest.default(msg = "无法审核非视频对象")
        }

        val video = targetRepositoryProvider.get(check.targetType).findByDeletedFalseAndPublishedFalseAndId(check.targetId) as Video
        if (input.expectSendTime == null) { //直接发布
            video.expectSendTime = ZonedDateTime.now()
            video.isSend = true
        } else {
            video.expectSendTime = quartzService.transFromStringToZonedDateTime(input.expectSendTime!!)
            video.isSend = false
            video.timeString = input.expectSendTime
        }
        videoService.save(video)

        if (input.result) {
            videoCheckService.check(check, user, CheckStatus.PASS, null)
        } else {
            val refusedReason = input.refusedReason ?: throw AppError.BadRequest.default(msg = "审核不通过时，需要填写拒绝原因。")
            videoCheckService.check(check, user, CheckStatus.REFUSE, refusedReason)
        }
    }

    @PreAuthorize("hasAuthority('CHECK_VIDEO')")
    @ApiOperation(
        value = "审核",
        notes = """
            1. result审核结果为false的时候，需要附加refusedReason参数。
            2. item = video
        """
    )
    @PutMapping("/{item}/{id}")
    fun check(
        @UserOrThrow user: User,
        @PathVariable item: String,
        @PathVariable id: UUID,
        @RequestBody input: CheckingInput
    ) {
        val check = checkService.findById(id)
            .orElseThrow { AppError.NotFound.default(msg = "找不到该审核") }

        if (input.result) {
            dispatch(item).check(check, user, CheckStatus.PASS, null)
        } else {
            val refusedReason = input.refusedReason ?: throw AppError.BadRequest.default(msg = "审核不通过时，需要填写拒绝原因。")
            dispatch(item).check(check, user, CheckStatus.REFUSE, refusedReason)
        }
    }

    fun dispatch(item: String): CheckService {
        return when (item) {
            "video" -> videoCheckService
            else -> throw AppError.Internal.default(msg = "服务器内部派送错误")
        }
    }
}
