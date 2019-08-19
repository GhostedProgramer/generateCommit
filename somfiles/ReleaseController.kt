package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.boostfield.spring.service.CompletionSuggestResult
import com.musicbible.event.EntityEvent
import com.musicbible.mapper.recording.RemoveRecordingArtistInput
import com.musicbible.mapper.recording.UpdateRecordingArtistInput
import com.musicbible.mapper.release.BackendWebReleaseListingOutput
import com.musicbible.mapper.release.BatchSwapTrackOrderInput
import com.musicbible.mapper.release.CreateTrackInput
import com.musicbible.mapper.release.FormattedTrackOutput
import com.musicbible.mapper.release.ReleaseArtistV1Output
import com.musicbible.mapper.release.ReleaseBackendDetailOutput
import com.musicbible.mapper.release.ReleaseBackendListInput
import com.musicbible.mapper.release.ReleaseBackendListOutput
import com.musicbible.mapper.release.ReleaseMapper
import com.musicbible.mapper.release.ReleaseRelationInput
import com.musicbible.mapper.release.ReleaseTimingInput
import com.musicbible.mapper.release.ReleaseTrackInput
import com.musicbible.mapper.release.TrackCreatedOutput
import com.musicbible.mapper.release.TrackListOutput
import com.musicbible.mapper.release.UpdateCatalogNumber
import com.musicbible.mapper.release.UpdateFormInput
import com.musicbible.mapper.release.UpdateMainArtistInput
import com.musicbible.mapper.release.UpdateMediaInput
import com.musicbible.mapper.release.UpdatePeriodInput
import com.musicbible.mapper.release.UpdateRecordingsArtistInput
import com.musicbible.mapper.release.UpdateReleaseArtistInput
import com.musicbible.mapper.release.UpdateReleaseInput
import com.musicbible.mapper.release.UpdateTrackInput
import com.musicbible.mapper.release.UpdateTypeInput
import com.musicbible.mapper.track.TrackMapper
import com.musicbible.mapper.trackGroup.TrackGroupMapper
import com.musicbible.mapper.trackGroup.TrackGroupReleaseOutput
import com.musicbible.model.DocumentStatus
import com.musicbible.model.Release
import com.musicbible.model.TrackGroup
import com.musicbible.model.User
import com.musicbible.security.CurrentUser
import com.musicbible.security.UserOrThrow
import com.musicbible.service.RecordingService
import com.musicbible.service.ReleaseArtistService
import com.musicbible.service.ReleaseService
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
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
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/release")
@Api(value = "/api/v0/release", tags = ["C 唱片"], description = "Release")
class ReleaseController(
    @Autowired val recordingService: RecordingService,
    @Autowired val releaseMapper: ReleaseMapper,
    @Autowired val releaseService: ReleaseService,
    @Autowired val releaseArtistService: ReleaseArtistService,
    @Autowired val trackMapper: TrackMapper,
    @Autowired val trackGroupMapper: TrackGroupMapper,
    @Autowired val userService: UserService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : BaseController() {

    @ApiOperation("艺术家列表")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}/artist")
    fun releaseArtistList(@PathVariable id: UUID): List<ReleaseArtistV1Output> {
        val release = releaseService.findExistsOrThrow(id)
        return releaseArtistService.findByRelease(release)
            .map(releaseMapper::toReleaseArtistV1Output)
    }

    @ApiOperation("删除关联的艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @DeleteMapping("/artist/{releaseArtistId}")
    fun removeReleaseArtist(@PathVariable releaseArtistId: UUID) {
        releaseArtistService.removeFromBackend(releaseArtistId)
    }

    @ApiOperation("设置关联的艺术家是否为主要艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/artist/main")
    fun updateMainReleaseArtist(@RequestBody input: UpdateMainArtistInput) {
        releaseArtistService.setAsMain(input.releaseArtistId, input.main)
    }

    @ApiOperation("同步艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/sync/artist")
    fun syncArtist(@CurrentUser user: User, @PathVariable id: UUID) {
        releaseService.syncReleaseArtistFromWorkAndRecording(id)
    }

    @ApiOperation("新建空唱片")
    @PreAuthorize("hasAuthority('CREATE_RELEASE')")
    @PostMapping
    fun create(@CurrentUser user: User): CreatedResponse {
        logger.info("Current User[{}]", user)
        var release = Release()
        release.beMaster = true
        release = releaseService.save(release)
        logger.info("Create Release[{}]", release.id)
        eventPublisher.publishEvent(EntityEvent.created(release))
        return RestResponse.created(release)
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): ReleaseBackendDetailOutput {
        val release = releaseService.findExistsOrThrow(id)
        return releaseMapper.toBackendDetail(release)
    }

    @ApiOperation("关联的主版本")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}/masters")
    fun masters(@PathVariable id: UUID): List<TrackGroupReleaseOutput> {
        val release = releaseService.findUnarchivedOrThrow(id)
        return release.trackGroups
            .filter { it.master != null }
            .map { tg ->
                trackGroupMapper.toTrackGroupReleaseOutput(tg)
                    .also { it.release = releaseMapper.toReleaseListingOutput(tg.master!!.release) }
            }
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping
    @ApiImplicitParam(
        name = "sort", allowableValues = "createdAt,commendLevel,catalogs.number,updatedAt",
        value = "createdAt:创建时间，commendLevel推荐等级，catalogs.number唱片编号,updatedAt:最后更新时间"
    )
    fun list(@Validated input: ReleaseBackendListInput): PageResponse<BackendWebReleaseListingOutput> {
        return releaseService.backendListSearch(input)
            .map(releaseMapper::toBackendWebEsReleaseListingOutput)
            .let(RestResponse::page)
    }

    @ApiOperation("音轨列表")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}/track")
    fun tracks(@PathVariable id: UUID): List<TrackListOutput> {
        val release = releaseService.findUnarchivedOrThrow(id)
        return release.tracks.map(trackMapper::toList)
    }

    @ApiOperation("结构化的音轨列表")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}/track/formatted")
    fun formattedTracks(@PathVariable id: UUID): List<FormattedTrackOutput> {
        return releaseService.formattedTracks(id)
    }

    @ApiOperation("发布")
    @PreAuthorize("hasAuthority('PUBLISH_RELEASE')")
    @PutMapping("/{id}/publish")
    fun publish(@UserOrThrow user: User, @PathVariable id: UUID) {
        val release = releaseService.findDraftOrThrow(id)
        releaseService.publish(user, release)
    }

    @ApiOperation("撤销发布")
    @PreAuthorize("hasAuthority('PUBLISH_RELEASE')")
    @PutMapping("/{id}/suppress")
    fun suppress(@UserOrThrow user: User, @PathVariable id: UUID) {
        val release = releaseService.findPublishedOrThrow(id)
        releaseService.suppress(user, release)
    }

    @ApiOperation("撤销用户唱片的发布")
    @PreAuthorize("hasAuthority('PUBLISH_RELEASE')")
    @PutMapping("/{id}/created_by_user/suppress")
    fun suppressReleaseCreatedByUser(@UserOrThrow user: User, @PathVariable id: UUID) {
        releaseService.suppressAndNotify(user, id)
    }

    @ApiOperation(value = "删除")
    @PreAuthorize("hasAuthority('DELETE_RELEASE')")
    @DeleteMapping("/{id}")
    fun delete(@UserOrThrow user: User, @PathVariable id: UUID) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.softDelete(user, release)
    }

    @ApiOperation(
        value = "删除用户创建的唱片",
        notes = """
            1. 唱片从属于用户
            2. 删除成功后发送通知
        """)
    @PreAuthorize("hasAuthority('DELETE_RELEASE')")
    @DeleteMapping("/{id}/created_by_user")
    fun deleteReleaseCreatedByUser(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestParam reason: String
    ) {
        releaseService.deleteAndNotify(user, id, reason)
    }

    @ApiOperation("将后台发布的唱片转换为马甲号发布,并创建前台动态")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/transToVest")
    fun transToVestPublish(@PathVariable id: UUID, @UserOrThrow user: User, @RequestBody input: ReleaseTimingInput) {
        releaseService.transToVest(id, user, input)
    }


    @ApiOperation("修改流派")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/genres")
    fun updateGenres(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody genreIds: List<UUID>
    ) {
        releaseService.updateGenres(user, id, genreIds)
    }

    @ApiOperation("修改风格")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/styles")
    fun updateStyles(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody styleIds: List<UUID>
    ) {
        releaseService.updateStyles(user, id, styleIds)
    }

    @ApiOperation("修改图片")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/images")
    fun updateImages(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody images: Array<String>
    ) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.updateImages(user, release, images)
    }

    @ApiOperation("修改PDF简介")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/pdfs")
    fun updatePdfs(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody pdfs: Array<Release.PDF>
    ) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.updatePDFs(user, release, pdfs)
    }

    @ApiOperation("修改时期")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/period")
    fun updatePeriod(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdatePeriodInput
    ) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.updatePeriod(user, release, body.periodId)
    }

    @ApiOperation("修改体裁")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/form")
    fun updateForm(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdateFormInput
    ) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.updateForm(user, release, body.formId)
    }

    @ApiOperation("修改乐器")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/instruments")
    fun updateInstruments(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody instrumentIds: List<UUID>
    ) {
        releaseService.updateInstruments(user, id, instrumentIds)
    }

    @ApiOperation("修改编号")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/catalogNumbers")
    fun updateCatalogNumber(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: List<UpdateCatalogNumber>
    ) {
        releaseService.updateCatalogs(user, id, body)
    }

    @ApiOperation("修改类型")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/type")
    fun updateType(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdateTypeInput
    ) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.updateType(user, release, body.typeId)
    }

    @ApiOperation("修改介质")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/media")
    fun updateMedia(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdateMediaInput
    ) {
        releaseService.updateMedia(user, id, body)
    }

    @ApiOperation("批量为录音增加艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{releaseId}/artists/batch")
    fun batchAddArtists(
        @RequestParam ids: Array<UUID>,
        @PathVariable releaseId: UUID,
        @Valid @RequestBody body: UpdateRecordingArtistInput
    ) {
        ids.forEach { id ->
            val recording = recordingService.findUnarchivedOrThrow(id)
            recordingService.addArtistAndSyncReleaseArtist(recording, body)
        }
    }

    @ApiOperation("批量为录音删除艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @DeleteMapping("/{releaseId}/artists/batch")
    fun batchDeleteArtists(
        @PathVariable releaseId: UUID,
        @Valid @RequestBody body: RemoveRecordingArtistInput
    ) {
        body.ids.forEach { id ->
            val recording = recordingService.findUnarchivedOrThrow(id)
            recordingService.removeArtistAndSyncReleaseArtist(recording, body)
        }
    }

    @ApiOperation("新增作品到音轨")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PostMapping("/{id}/track/by/work/{workId}")
    fun addTrackByWork(
        @PathVariable id: UUID, @PathVariable workId: UUID, @Valid @RequestBody body: CreateTrackInput
    ): TrackCreatedOutput {
        val track = releaseService.addTrackByWork(id, workId, body)
        return trackMapper.toCreated(track)
    }

    @ApiOperation("新增乐章到音轨")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PostMapping("/{id}/track/by/movements")
    fun addTrackByMovements(
        @PathVariable id: UUID, @Valid @RequestBody body: List<UUID>
    ): List<TrackCreatedOutput> {
        return releaseService.addTrackByMovements(id, body)
            .map(trackMapper::toCreated)
    }

    @ApiOperation("批量修改录音艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/recordings/artists")
    fun updateRecordingsArtists(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdateRecordingsArtistInput
    ) {
        releaseService.updateRecordingsArtists(user, id, body)
    }

    @ApiOperation("为所有录音新增艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PostMapping("/{id}/recordings/artist")
    fun addRecordingsArtist(@PathVariable id: UUID, @Valid @RequestBody body: UpdateRecordingArtistInput) {
        releaseService.addRecordingsArtist(id, body)
    }

    @ApiOperation("删除音轨")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @DeleteMapping("/{id}/track/{trackId}")
    fun deleteTrack(@PathVariable id: UUID, @PathVariable trackId: UUID) {
        releaseService.deleteTracks(id, listOf(trackId))
    }

    @ApiOperation("批量删除音轨")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @DeleteMapping("/track")
    fun deleteTrackList(@RequestBody inputs: List<ReleaseTrackInput>) {
        inputs.forEach { releaseService.deleteTracks(it.releaseId, it.trackIds) }
    }

    @ApiOperation("交换音轨顺序")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/trackGroup/{id}/track/order/{xId}/swap/{yId}")
    fun swapTrackOrder(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @PathVariable xId: UUID,
        @PathVariable yId: UUID
    ) {
        releaseService.swapTrackOrder(user, id, xId, yId)
    }

    @ApiOperation("批量交换音轨顺序", notes = "参数为音轨在唱片中的 index")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/trackGroup/{id}/track/order")
    fun batchSwapTrackOrder(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: BatchSwapTrackOrderInput
    ) {
        releaseService.batchSwapTrackOrder(user, id, body.source, body.dest)
    }

    @ApiOperation("修改音轨信息")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/track/{trackId}")
    fun updateTrack(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @PathVariable trackId: UUID,
        @Valid @RequestBody body: UpdateTrackInput
    ) {
        releaseService.updateTrackInfo(user, id, trackId, body)
    }

    @ApiOperation("修改艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/artists")
    fun updateArtists(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: List<UpdateReleaseArtistInput>
    ) {
        releaseService.updateArtists(user, id, body)
    }

    @ApiOperation("修改基本字段")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}")
    fun updateFields(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdateReleaseInput
    ) {
        val release = releaseService.findUnarchivedOrThrow(id)
        releaseService.updateFields(user, release, body)
    }

    @ApiOperation("设置为主版本")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/as/master")
    fun setAsMaster(@UserOrThrow user: User, @PathVariable id: UUID) {
        releaseService.setAsMaster(user, id)
    }

    @ApiOperation("关联到主版本", notes =
    """
        如果一个唱片，本身已经有了音轨，但是却选择了关联某个主版本
        会给出提示"唱片已有音轨，若关联将会导致原音轨被覆盖"
        如果用户决定强制关联，则必须带上force参数，那么此时会清空该唱片的原有音轨
        这是为了保证当一张唱片只关联了一个主版本时，确保有且只有一个音轨组且是继承自主版本的
        而不至于有两个音轨组一个继承来的一个自带的
    """
    )
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/relation/master")
    fun relationToMaster(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody input: ReleaseRelationInput
    ) {
        releaseService.relationToMaster(user, id, input.masterIds, input.force)
    }

    @ApiOperation("与主版本解除关联", notes =
    """
        解除关联后，子版本的音轨仍然保留
        如果解除关联后该唱片不再包含任何关联关系，会自动变成主版本
    """
    )
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{subjectId}/clearRelation")
    fun clearRelationToMaster(
        @UserOrThrow user: User,
        @PathVariable subjectId: UUID,
        @RequestBody masterIds: List<UUID>
    ): ReleaseBackendDetailOutput {
        return releaseService.clearRelationToMaster(user, subjectId, masterIds)
            .let(releaseMapper::toBackendDetail)
    }

    @ApiOperation("同步到子版本", notes = "返回的是被同步过的子唱片（已同步唱片就不再同步了）")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/fetch")
    fun fetchToSubjects(@UserOrThrow user: User, @PathVariable id: UUID) {
        releaseService.fetchSubject(user, id)
    }

    @ApiOperation("同版本唱片")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}/same/master")
    fun sameEditions(@PathVariable id: UUID): List<ReleaseBackendListOutput> {
        return releaseService
            .sameEditions(id, listOf(DocumentStatus.PUBLISHED, DocumentStatus.DRAFT, DocumentStatus.CHECKING))
            .map(releaseMapper::toBackendList)
    }

    @ApiOperation("复制唱片", notes = """
        复制唱片分为从主版本复制和从子版本复制
        如果从主版本复制，则直接关联到该主版本
        如果从子版本复制，则直接关联到该子版本的主版本
        如果不想关联，加参数relation:false(可选)
        但是不建议这样做，历史遗留问题所以暂时保留这个方案
        具体看关联主版本的说明
        """
    )
    @PreAuthorize("hasAuthority('CREATE_RELEASE')")
    @GetMapping("/{id}/copy")
    fun copy(
        @PathVariable id: UUID,
        @ApiParam(required = false)
        @RequestParam(defaultValue = "true")
        relation: Boolean = true
    ): CreatedResponse {
        return RestResponse.created(
            if (relation) releaseService.copyAsSameEdition(id)
            else releaseService.copyForEdit(id)
        )
    }

    @ApiOperation("关联的主版本上下移动", notes = "主版本的上下移动本质上是trackGroup的上下移动，需要传trackGroupId")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/trackGroup/{trackGroupId}/order")
    fun fetchToSubjects(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @PathVariable trackGroupId: UUID,
        @RequestParam move: TrackGroup.Move
    ) {
        releaseService.updateTrackGroupOrder(user, id, trackGroupId, move)
    }

    @ApiOperation("搜索建议")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/autoCompletion")
    fun suggest(@RequestParam word: String): CompletionSuggestResult {
        return releaseService.backendCompleteionSuggest(word)
    }

    @ApiOperation("主版本关联的子版本（排除同版本）")
    @PreAuthorize("hasAuthority('READ_RELEASE')")
    @GetMapping("/{id}/subjects")
    fun subjects(
        @PathVariable id: UUID,
        @RequestParam q: String?,
        pageQuery: PageQuery
    ): PageResponse<ReleaseBackendListOutput> {
        val content: MutableList<Release> = releaseService.getSubjectsOfTheMaster(id, q, pageQuery).content
        val sameEditions = releaseService.sameEditions(id, listOf(DocumentStatus.PUBLISHED, DocumentStatus.DRAFT, DocumentStatus.CHECKING))
        val filter = content.filter { !sameEditions.contains(it) }
        val map = filter.map(releaseMapper::toBackendList)
        return PageResponse(map, map.size.toLong())
    }
}
