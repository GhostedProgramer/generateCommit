package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.billboard.AssociateReleaseInput
import com.musicbible.mapper.billboard.BillboardColumnOutput
import com.musicbible.mapper.billboard.BillboardDetailOutput
import com.musicbible.mapper.billboard.BillboardInput
import com.musicbible.mapper.billboard.BillboardMapper
import com.musicbible.mapper.billboard.BillboardReleaseRankSwapInput
import com.musicbible.mapper.billboard.Key
import com.musicbible.mapper.release.BackendBillboardReleaseOutput
import com.musicbible.mapper.release.ReleaseMapper
import com.musicbible.model.Billboard
import com.musicbible.model.QBillboard
import com.musicbible.service.BillboardService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
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
@RequestMapping("/api/v0/billboard")
@Api("/api/v0/billboard", tags = ["B 榜单"], description = "Billboard")
class BillboardController(
    @Autowired val billboardService: BillboardService,
    @Autowired val billboardMapper: BillboardMapper,
    @Autowired val releaseMapper: ReleaseMapper
) {

    @ApiOperation("新建空榜单")
    @PreAuthorize("hasAuthority('CREATE_BILLBOARD')")
    @PostMapping
    fun create(): CreatedResponse {
        val billboard = billboardService.save(Billboard())
        return RestResponse.created(billboard.id)
    }

    /**
     * 榜单列表点进来需要将已有的信息带到编辑页面
     * */
    @ApiOperation("预览")
    @PreAuthorize("hasAuthority('READ_BILLBOARD')")
    @GetMapping("/{id}")
    fun billBoardDetail(@PathVariable id: UUID): BillboardDetailOutput {
        val billboard = billboardService.findExistsOrThrow(id)
        return billboardMapper.toDetailOutput(billboard)
    }

    /**中文全称  外文全称  排序  描述  权重 五个字段*/
    @ApiOperation("修改基本信息")
    @PreAuthorize("hasAuthority('UPDATE_BILLBOARD')")
    @PutMapping("/{id}")
    fun updateFields(@PathVariable id: UUID, @Valid @RequestBody updateBillboardInput: BillboardInput) {
        billboardService.updateFields(id, updateBillboardInput)
    }

    @ApiOperation("修改图片")
    @PreAuthorize("hasAuthority('UPDATE_BILLBOARD')")
    @PutMapping("/{id}/images")
    fun updateImages(@PathVariable id: UUID, @Valid @RequestBody images: Array<String>) {
        billboardService.updateImages(id, images)
    }

    @ApiOperation("删除榜单")
    @PreAuthorize("hasAuthority('DELETE_BILLBOARD')")
    @DeleteMapping("/{id}")
    fun deleteBillboard(@PathVariable id: UUID) {
        billboardService.delete(id)
    }

    @ApiOperation("榜单列表")
    @PreAuthorize("hasAuthority('READ_BILLBOARD')")
    @GetMapping
    @ApiImplicitParam(name = "sort", value = "createdAt:创建时间,-commendLevel:推荐等级", allowableValues = "createdAt,-commendLevel")
    fun list(
        @Valid pageQuery: PageQuery,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) key: Key?
    ): PageResponse<BillboardColumnOutput> {
        var criteria = QBillboard.billboard.deleted.isFalse
        q?.also {
            when (key) {
                Key.NAME -> criteria = criteria.and(QBillboard.billboard.name.contains(q).or(
                    QBillboard.billboard.nameCN.contains(q)
                ))
                Key.CREATOR -> criteria = criteria.and(
                    QBillboard.billboard.createdBy.nickName.contains(q).or(
                        QBillboard.billboard.createdBy.userName.contains(q)
                    ))
            }
        }
        return billboardService.findAll(criteria, pageQuery.pageable())
            .map(billboardMapper::toColumnOutput)
            .let(RestResponse::page)
    }

    @ApiOperation("已关联的唱片列表")
    @PreAuthorize("hasAuthority('READ_BILLBOARD')")
    @GetMapping("/{id}/release")
    fun listRelease(@PathVariable id: UUID): List<BackendBillboardReleaseOutput> {
        return billboardService.getRankOrderedRelease(id)
            .map(releaseMapper::toBackendBillboardReleaseOutput)
    }

    @ApiOperation("关联唱片")
    @PreAuthorize("hasAuthority('UPDATE_BILLBOARD')")
    @PutMapping("/{id}/release")
    fun associateRelease(@PathVariable id: UUID, @Valid @RequestBody input: AssociateReleaseInput) {
        billboardService.associateRelease(id, input.releaseId)
    }

    @ApiOperation("删除关联唱片")
    @PreAuthorize("hasAuthority('DELETE_BILLBOARD')")
    @DeleteMapping("/{id}/release/{releaseId}")
    fun deleteRelease(@PathVariable id: UUID, @Valid @PathVariable(name = "releaseId") releaseId: UUID) {
        billboardService.unAssociateRelease(id, releaseId)
    }

    @ApiOperation("交换唱片的顺序")
    @PreAuthorize("hasAuthority('UPDATE_BILLBOARD')")
    @PutMapping("/{id}/swapRelease")
    fun swapRelease(@PathVariable id: UUID, @Valid @RequestBody input: BillboardReleaseRankSwapInput) {
        billboardService.swapReleaseRank(id, input.fromId, input.toId)
    }
}
