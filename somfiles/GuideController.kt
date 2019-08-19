package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.guide.GuideCategoryDetailOutput
import com.musicbible.mapper.guide.GuideCategoryInput
import com.musicbible.mapper.guide.GuideCategoryOutput
import com.musicbible.mapper.guide.GuideCodeinput
import com.musicbible.mapper.guide.GuideInput
import com.musicbible.mapper.guide.GuideMapper
import com.musicbible.mapper.guide.GuideOutput
import com.musicbible.mapper.guide.GuideTitleInput
import com.musicbible.mapper.guide.MoveInput
import com.musicbible.service.GuideCategoryService
import com.musicbible.service.GuideService
import io.swagger.annotations.Api
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
@RequestMapping("/api/v0/guideCategory")
@Api(value = "/api/v0/guideCategory", tags = ["Z 指南"], description = "Guide")
class GuideController(
    @Autowired val guideService: GuideService,
    @Autowired val guideCategoryService: GuideCategoryService,
    @Autowired val guideMapper: GuideMapper
) {

    @ApiOperation("模块详情")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @GetMapping("/{id}")
    fun categoryDetail(@PathVariable id: UUID): GuideCategoryDetailOutput {
        val guideCategory = guideCategoryService.findOrThrow(id)
        return guideMapper.toDetail(guideCategory)
    }


    @ApiOperation("快速编辑模块名")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @PutMapping("/{id}/module")
    fun quickUpdateModule(@PathVariable id: UUID, @RequestBody input: GuideTitleInput) {
        guideCategoryService.quickUpdateModule(id, input)
    }

    @ApiOperation("快速编辑模块code")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @PutMapping("/{id}/code")
    fun quickUpdateCode(@PathVariable id: UUID, @RequestBody input: GuideCodeinput) {
        guideCategoryService.quickUpdateCode(id, input)
    }

    @ApiOperation("新建模块")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @PostMapping
    fun create(@Valid @RequestBody input: GuideCategoryInput): CreatedResponse {
        val item = guideCategoryService.createCategory(input)
        return RestResponse.created(item.id)
    }

    @ApiOperation("删除模块")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        guideCategoryService.findOrThrow(id)
            .also(guideCategoryService::delete)
    }

    @ApiOperation("删除模块内容")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @DeleteMapping("/guide/{id}")
    fun deleteGuide(@PathVariable id: UUID) {
        guideService.findOrThrow(id)
            .also(guideService::delete)
    }

    @ApiOperation("新建/编辑指南")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @PostMapping("/{id}/guide")
    fun createGuide(
        @PathVariable id: UUID, @Valid @RequestBody guideInput: List<GuideInput>
    ): List<GuideOutput> {
        return guideCategoryService.createGuide(id, guideInput)
    }

    @ApiOperation("模块列表")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @GetMapping
    fun list(
        @Valid pageQuery: PageQuery, @RequestParam(required = false) match: String?
    ): List<GuideCategoryOutput> {
        return if (match.isNullOrEmpty()) {
            guideCategoryService.findAll().sortedBy { it.order }
        } else {
            guideCategoryService.findByModuleContaining(match).sortedBy { it.order }
        }.let(guideMapper::toListOfGuideCategoryOutput)
    }

    @ApiOperation("模块分页列表")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @GetMapping("/paging")
    fun paging(
        @Valid pageQuery: PageQuery,
        @RequestParam(required = false) match: String?
    ): PageResponse<GuideCategoryOutput> {
        val page = guideCategoryService.list(match, pageQuery)
        val guideCategoryPages = page.map(guideMapper::toGuideCategoryOutput)
        return RestResponse.page(guideCategoryPages)
    }

    @ApiOperation("模块内容列表")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @GetMapping("/{id}/guide")
    fun guideList(@PathVariable id: UUID): List<GuideOutput> {
        return guideCategoryService.findOrThrow(id)
            .let {
                guideMapper.toListOfGuideOutput(it.guides)
            }
    }

    @ApiOperation("模块修改{上下移}")
    @PreAuthorize("hasAuthority('MANAGE_GUIDE')")
    @PutMapping("/move")
    fun moveGuide(@Valid @RequestBody moveInput: MoveInput) {
        guideCategoryService.moveGuide(moveInput)
    }
}
