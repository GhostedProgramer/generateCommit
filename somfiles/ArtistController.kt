package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.TermsAggregationResult
import com.musicbible.mapper.artist.*
import com.musicbible.model.ArtistTypeEnum
import com.musicbible.service.ArtistService
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
@RequestMapping("/api/v0/artist")
@Api(value = "/api/v0/artist", tags = ["Y 艺术家"], description = "Artist")
class ArtistController(
    @Autowired val userService: UserService,
    @Autowired val artistMapper: ArtistMapper,
    @Autowired val artistService: ArtistService
) : BaseController() {

    @ApiOperation("新建空艺术家")
    @PreAuthorize("hasAuthority('CREATE_ARTIST')")
    @PostMapping
    fun create(): CreatedResponse {
        val artist = artistService.create()
        return RestResponse.created(artist.id)
    }

    @ApiOperation("艺术家成就下以职业聚合")
    @PreAuthorize("hasAuthority('READ_ARTIST')")
    @GetMapping("/{id}/aggregation/profession")
    fun aggOnProfession(@PathVariable id: UUID): TermsAggregationResult {
        return artistService.aggregationOnProfession(id)
    }

    @ApiOperation("特殊艺术家")
    @PreAuthorize("hasAuthority('READ_ARTIST')")
    @GetMapping("/special")
    fun specials(): List<ArtistIdentityOutput> {
        return artistService
            .findByType(ArtistTypeEnum.SPECIAL)
            .map(artistMapper::toIdentity)
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('READ_ARTIST')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): ArtistDetailOutput {
        val artist = artistService.findExistsOrThrow(id)
        return artistMapper.toDetail(artist)
    }

    @ApiOperation("成员列表")
    @PreAuthorize("hasAuthority('READ_ARTIST')")
    @GetMapping("/{id}/members")
    fun members(@PathVariable id: UUID): List<ArtistIdentityOutput> {
        val artist = artistService.findExistsOrThrow(id)
        return artist.members
            .map(artistMapper::toIdentity)
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('READ_ARTIST')")
    @GetMapping
    @ApiImplicitParam(
        name = "sort",
        value = "commendLevel:推荐等级,updatedAt:最后更新时间,createdAt：创建时间,releaseCount:唱片数量",
        allowableValues = "commendLevel,updatedAt,releaseCount"
    )
    fun list(@Validated input: ArtistBackendListInput): PageResponse<BackendWebArtistListingOutput> {
        return artistService.backendListSearch(input)
            .map(artistMapper::toBackendWebEsListing)
            .let(RestResponse::page)
    }

    @ApiOperation("发布")
    @PreAuthorize("hasAuthority('PUBLISH_ARTIST')")
    @PutMapping("/{id}/publish")
    fun publish(@PathVariable id: UUID) {
        artistService.publish(id)
    }

    @ApiOperation("撤销发布")
    @PreAuthorize("hasAuthority('PUBLISH_ARTIST')")
    @PutMapping("/{id}/suppress")
    fun suppress(@PathVariable id: UUID) {
        artistService.suppress(id)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('DELETE_ARTIST')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        artistService.softDelete(id)
    }

    @ApiOperation("修改图片")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/images")
    fun updateImages(@PathVariable id: UUID, @Valid @RequestBody images: Array<String>) {
        artistService.updateImages(id, images)
    }

    @ApiOperation("修改国籍")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/nationality")
    fun updateCountry(@PathVariable id: UUID, @Valid @RequestBody body: UpdateNationality) {
        artistService.updateNationality(id, body.countryId)
    }

    @ApiOperation("修改流派")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/genres")
    fun updateGenres(@PathVariable id: UUID, @Valid @RequestBody genreIds: List<UUID>) {
        artistService.updateGenres(id, genreIds)
    }

    @ApiOperation("修改风格")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/styles")
    fun updateStyles(@PathVariable id: UUID, @Valid @RequestBody styleIds: List<UUID>) {
        artistService.updateStyles(id, styleIds)
    }

    @ApiOperation("修改时期")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/period")
    fun updatePeriod(@PathVariable id: UUID, @Valid @RequestBody body: UpdatePeriodInput) {
        artistService.updatePeriod(id, body.periodId)
    }

    @ApiOperation("修改职业")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/professions")
    fun updateProfession(@PathVariable id: UUID, @Valid @RequestBody professionIds: List<UUID>) {
        artistService.updateProfessions(id, professionIds)
    }

    @ApiOperation("修改基本字段")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}")
    fun updateFields(@PathVariable id: UUID, @Valid @RequestBody body: UpdateArtistInput) {
        artistService.updateFields(id, body)
    }

    @ApiOperation("修改成员")
    @PreAuthorize("hasAuthority('UPDATE_ARTIST')")
    @PutMapping("/{id}/members")
    fun updateMembers(@PathVariable id: UUID, @Valid @RequestBody body: List<UUID>) {
        artistService.updateMembers(id, body)
    }

    @ApiOperation("搜索建议")
    @PreAuthorize("hasAuthority('READ_ARTIST')")
    @GetMapping("/autoCompletion")
    fun suggest(@RequestParam word: String): CompletionSuggestResult {
        return artistService.backendCompleteionSuggest(word)
    }
}
