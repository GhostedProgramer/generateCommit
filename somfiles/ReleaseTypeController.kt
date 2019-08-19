package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.ReleaseType
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.ReleaseTypeService
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
@RequestMapping("/api/v0/releaseType")
@Api(value = "/api/v0/releaseType", tags = ["C 唱片类型"], description = "ReleaseType")
class ReleaseTypeController(
    @Autowired val releaseTypeService: ReleaseTypeService,
    @Autowired val categoryCacheService: CategoryCacheService
) {

    @ApiOperation("新建唱片属性")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun addPeriod(@Valid @RequestBody req: CreateNameInput): CreatedResponse {
        val releaseType = releaseTypeService.save(ReleaseType(req.nameCN, req.nameEN))
        categoryCacheService.refresh()
        return RestResponse.created(releaseType)
    }

    @ApiOperation("修改唱片属性分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateReleaseType(@PathVariable id: UUID, @Valid @RequestBody input: CreateNameInput) {
        val releaseType = releaseTypeService.findOrThrow(id)
        categoryCacheService.refresh()
        releaseTypeService.update(releaseType, input)
    }

    @ApiOperation("删除唱片属性")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun removePeriod(@PathVariable id: UUID) {
        releaseTypeService.deleteAndCheck(id)
    }

    @ApiOperation("获取所有唱片属性")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    fun getPeriods(): List<ReleaseType> = releaseTypeService.findAll(Sort.by(Sort.Direction.DESC, "weight"))

    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<ReleaseType> {
        categoryCacheService.refresh()
        return releaseTypeService.sort(ids)
    }
}
