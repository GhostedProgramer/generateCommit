package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.mapper.profession.CreateProfessionInput
import com.musicbible.mapper.profession.ProfessionMapper
import com.musicbible.model.Profession
import com.musicbible.model.ProfessionType
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.ProfessionService
import com.musicbible.service.ProfessionTypeService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
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
@RequestMapping("/api/v0/profession")
@Api(value = "/api/v0/profession", tags = ["Z 职业"], description = "Profession")
class ProfessionController(
    @Autowired val professionService: ProfessionService,
    @Autowired val professionTypeService: ProfessionTypeService,
    @Autowired val professionMapper: ProfessionMapper,
    @Autowired val categoryCacheService: CategoryCacheService
) {

    @ApiOperation("新建职业类型")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping("/type")
    fun addProfessionType(@Valid @RequestBody req: CreateNameInput): CreatedResponse {
        val professionType = professionTypeService.save(ProfessionType(req.nameCN, req.nameEN))
        categoryCacheService.refresh()
        return RestResponse.created(professionType)
    }

    @ApiOperation("新建艺术家职业")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun addProfession(@Valid @RequestBody req: CreateProfessionInput): CreatedResponse {
        val profession = professionService.add(req)
        categoryCacheService.refresh()
        return RestResponse.created(profession)
    }

    @ApiOperation("删除职业类型")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/type/{id}")
    fun removeProfessionType(@PathVariable id: UUID) {
        professionTypeService.deleteAndCheck(id)
    }

    @ApiOperation("删除艺术家职业")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun removeProfession(@PathVariable id: UUID) {
        professionService.deleteAndCheck(id)
    }


    @ApiOperation("修改职业分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/type/{id}")
    fun updateProfessionType(@PathVariable id: UUID, @Valid @RequestBody input: CreateNameInput) {
        val professionType = professionTypeService.findOrThrow(id)
        categoryCacheService.refresh()
        professionTypeService.updateType(professionType, input)
    }

    @ApiOperation("修改艺术家职业分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateProfession(@PathVariable id: UUID, @Valid @RequestBody input: CreateProfessionInput) {
        val profession = professionService.findOrThrow(id)
        categoryCacheService.refresh()
        professionService.update(profession, input)
    }


    @ApiOperation("获取所有类型和职业")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    @Transactional
    fun getProfessions() = professionTypeService
        .findAll(Sort.by(Sort.Direction.DESC, "weight"))
        .map(professionMapper::professionTypeToProfessionTypeOutput)

    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Profession> {
        categoryCacheService.refresh()
        return professionService.sort(ids)
    }

    @ApiOperation("更改类型排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/type")
    fun typeChangeLocation(@RequestBody ids: List<UUID>): MutableList<ProfessionType> {
        categoryCacheService.refresh()
        return professionTypeService.sort(ids)
    }
}
