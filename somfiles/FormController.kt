package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Form
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.FormService
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

@RestController
@RequestMapping("/api/v0/form")
@Api(value = "/api/v0/form", tags = ["T 体载"], description = "Form")
class FormController(
    @Autowired val formService: FormService,
    @Autowired val categoryCacheService: CategoryCacheService
) {
    @ApiOperation("新建体载")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun add(@Valid @RequestBody req: CreateNameInput): CreatedResponse {
        val form = formService.create(req.nameCN, req.nameEN)
        categoryCacheService.refresh()
        return RestResponse.created(form)
    }

    @ApiOperation("修改体裁分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateForm(@PathVariable id: UUID, @Valid @RequestBody input: CreateNameInput) {
        val form = formService.findOrThrow(id)
        categoryCacheService.refresh()
        formService.update(form, input)
    }

    @ApiOperation("删除体载")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        formService.deleteAndCheck(id)
    }

    @ApiOperation("获取体载")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    fun list(): List<Form> = formService.findAll(Sort.by(Sort.Direction.DESC, "weight"))

    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Form> {
        categoryCacheService.refresh()
        return formService.sort(ids)
    }
}
