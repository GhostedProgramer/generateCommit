package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.genre.CreateGenreInput
import com.musicbible.model.Genre
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.GenreService
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

@Api(value = "/api/v0/genre", tags = ["L 流派"], description = "Genre")
@RestController
@RequestMapping("/api/v0/genre")
class GenreController(
    @Autowired val genreService: GenreService,
    @Autowired val categoryCacheService: CategoryCacheService
) : BaseController() {

    @ApiOperation("全部")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    fun all(): List<Genre> = genreService.findAll(Sort.by(Sort.Direction.DESC, "weight"))

    @ApiOperation("增加")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun add(@Valid @RequestBody input: CreateGenreInput): CreatedResponse {
        // todo 检查是否有同名
        val genre = genreService.create(input)
        categoryCacheService.refresh()
        return RestResponse.created(genre)
    }

    @ApiOperation("修改流派分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateGnere(@PathVariable id: UUID, @Valid @RequestBody input: CreateGenreInput) {
        val genre = genreService.findOrThrow(id)
        categoryCacheService.refresh()
        genreService.update(genre, input)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun rm(@PathVariable id: UUID) {
        genreService.deleteAndCheck(id)
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping("/{id}")
    fun one(@PathVariable id: UUID) = genreService.findOrThrow(id)

    @ApiOperation("更改排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Genre> {
        categoryCacheService.refresh()
        return genreService.sort(ids)
    }
}
