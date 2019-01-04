package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.country.CountryMapper
import com.musicbible.mapper.country.CountrySimpleOutput
import com.musicbible.mapper.country.CreateCountryInput
import com.musicbible.model.Country
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.CountryService
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

@Api(value = "/api/v0/country", tags = ["G 国家"], description = "Country")
@RestController
@RequestMapping("/api/v0/country")
class CountryController(
    @Autowired val countryService: CountryService,
    @Autowired val countryMapper: CountryMapper,
    @Autowired val categoryCacheService: CategoryCacheService
) : BaseController() {

    @ApiOperation("全部")
    @GetMapping
    fun all(): List<CountrySimpleOutput> = countryService.findAll(Sort.by(Sort.Direction.DESC,"weight")).map(countryMapper::toSimple)

    @ApiOperation("新建国家")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun addConutry(@Valid @RequestBody input: CreateCountryInput): CreatedResponse {
        val country = countryService.create(input)
        categoryCacheService.refresh()
        return RestResponse.created(country)
    }

    @ApiOperation("修改国家分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateConutry(@PathVariable id: UUID, @Valid @RequestBody input: CreateCountryInput) {
        val country = countryService.findOrThrow(id)
        categoryCacheService.refresh()
        countryService.update(country, input)
    }

    @ApiOperation("删除国家")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun removeCountry(@PathVariable id: UUID) {
        countryService.deleteAndCheck(id)
    }

    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Country> {
        categoryCacheService.refresh()
        return countryService.sort(ids)
    }
}
