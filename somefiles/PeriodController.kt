package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Period
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.PeriodService
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
@RequestMapping("/api/v0/period")
@Api(value = "/api/v0/period", tags = ["S 时期"], description = "Period")
class PeriodController(
    @Autowired val periodService: PeriodService,
    @Autowired val categoryCacheService: CategoryCacheService
) {

    @ApiOperation("新建音乐时期")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun addPeriod(@Valid @RequestBody req: CreateNameInput): CreatedResponse {
        val period = periodService.save(Period(req.nameCN, req.nameEN))
        categoryCacheService.refresh()
        return RestResponse.created(period)
    }

    @ApiOperation("删除音乐时期")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun removePeriod(@PathVariable id: UUID) {
        periodService.deleteAndCheck(id)
    }

    @ApiOperation("修改音乐时期分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updatePeriod(@PathVariable id: UUID, @Valid @RequestBody input: CreateNameInput) {
        val period = periodService.findOrThrow(id)
        categoryCacheService.refresh()
        periodService.update(period, input)
    }

    @ApiOperation("获取所有音乐时期")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    fun getPeriods(): List<Period> = periodService.findAll(Sort.by(Sort.Direction.DESC, "weight"))


    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Period> {
        categoryCacheService.refresh()
        return periodService.sort(ids)
    }
}
