package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.genre.CreateStyleInput
import com.musicbible.model.Style
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.StyleService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@Api(value = "/api/v0/style", tags = ["F 风格"], description = "Style")
@RestController
@RequestMapping("/api/v0/style")
class StyleController(
    @Autowired val styleService: StyleService,
    @Autowired val categoryCacheService: CategoryCacheService
) : BaseController() {

    @ApiOperation("增加")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun add(@Valid @RequestBody input: CreateStyleInput): CreatedResponse {
        // todo 检查是否有同名
        val style = styleService.create(input)
        categoryCacheService.refresh()
        return RestResponse.created(style)
    }

    @ApiOperation("修改风格分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateStyle(@PathVariable id: UUID, @Valid @RequestBody input: CreateStyleInput) {
        val style = styleService.findOrThrow(id)
        categoryCacheService.refresh()
        styleService.update(style, input)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun rm(@PathVariable id: UUID) {
        styleService.deleteAndCheck(id)
    }

    @ApiOperation("所有")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    fun all(): List<Style> = styleService.findAll(Sort.by(Sort.Direction.DESC, "weight"))

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping("/{id}")
    fun one(@PathVariable("id") id: UUID): Style = styleService.findOrThrow(id)

    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Style> {
        categoryCacheService.refresh()
        return styleService.sort(ids)
    }
}
