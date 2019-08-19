package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.service.CompletionSuggestResult
import com.musicbible.mapper.label.*
import com.musicbible.service.LabelService
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/label")
@Api(value = "/api/v0/label", tags = ["C 厂牌"], description = "Label")
class LabelController(
    @Autowired val userService: UserService,
    @Autowired val labelService: LabelService,
    @Autowired val labelMapper: LabelMapper
) : BaseController() {

    @ApiOperation("新建空厂牌")
    @PreAuthorize("hasAuthority('CREATE_LABEL')")
    @PostMapping
    fun create(): CreatedResponse {
        return RestResponse.created(labelService.create())
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('READ_LABEL')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): LabelDetailOutput {
        val label = labelService.findExistsOrThrow(id)
        return labelMapper.toDetail(label)
    }

    @ApiOperation("列表")
    @ApiImplicitParam(
        name = "sort",
        value = "createdAt:创建时间,updatedAt:最后更新时间,commendLevel:权重,releaseCount:唱片数",
        allowableValues = "createdAt,updatedAt,commendLevel"
    )
    @PreAuthorize("hasAuthority('READ_LABEL')")
    @GetMapping
    fun list(@Validated input: BackendListInput): PageResponse<BackendWebLabelListingOutput> {
        return labelService.backendListSearch(input)
            .map(labelMapper::toBackendWebEsLabelListingOutput)
            .let(RestResponse::page)
    }

    @ApiOperation("发布")
    @PreAuthorize("hasAuthority('PUBLISH_LABEL')")
    @PutMapping("/{id}/publish")
    fun publish(@PathVariable id: UUID) {
        labelService.publish(id)
    }

    @ApiOperation("撤销发布")
    @PreAuthorize("hasAuthority('PUBLISH_LABEL')")
    @PutMapping("/{id}/suppress")
    fun suppress(@PathVariable id: UUID) {
        labelService.suppress(id)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('DELETE_LABEL')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        labelService.softDelete(id)
    }

    @ApiOperation("修改父厂牌")
    @PreAuthorize("hasAuthority('UPDATE_LABEL')")
    @PutMapping("/{id}/parent")
    fun updateParent(@PathVariable id: UUID, @Valid @RequestBody body: UpdateParent) {
        labelService.updateParent(id, body.parentId)
    }

    @ApiOperation("修改国家")
    @PreAuthorize("hasAuthority('UPDATE_LABEL')")
    @PutMapping("/{id}/country")
    fun updateCountry(@PathVariable id: UUID, @Valid @RequestBody body: UpdateCountry) {
        labelService.updateCountry(id, body.countryId)
    }

    @ApiOperation("修改图片")
    @PreAuthorize("hasAuthority('UPDATE_LABEL')")
    @PutMapping("/{id}/images")
    fun updateImages(@PathVariable id: UUID, @Valid @RequestBody images: Array<String>) {
        labelService.updateImages(id, images)
    }

    @ApiOperation("修改基本字段")
    @PreAuthorize("hasAuthority('UPDATE_LABEL')")
    @PutMapping("/{id}")
    fun updateFields(@PathVariable id: UUID, @Valid @RequestBody updateLabelInput: UpdateLabelInput) {
        labelService.updateFields(id, updateLabelInput)
    }

    @ApiOperation("搜索建议")
    @PreAuthorize("hasAuthority('READ_LABEL')")
    @GetMapping("/autoCompletion")
    fun suggest(@RequestParam word: String): CompletionSuggestResult {
        return labelService.backendCompleteionSuggest(word)
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAuthority('READ_LABEL')")
    @ApiOperation("获取所有子厂牌")
    fun getChildren(@PathVariable("id") id: UUID): List<LabelSimpleOutput> {
        return labelService.listChildren(id).map(labelMapper::toSimple)
    }
}

