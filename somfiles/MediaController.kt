package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.media.CreateDimensionInput
import com.musicbible.mapper.media.CreateMediaFeatureInput
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.mapper.media.DimensionOutput
import com.musicbible.mapper.media.FeatureOutput
import com.musicbible.mapper.media.MediaMapper
import com.musicbible.mapper.media.MediaOutput
import com.musicbible.model.Media
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.MediaDimensionService
import com.musicbible.service.MediaFeatureService
import com.musicbible.service.MediaService
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
@RequestMapping("/api/v0/media")
@Api(value = "/api/v0/media", tags = ["J 介质"], description = "Media")
class MediaController(
    @Autowired val mediaService: MediaService,
    @Autowired val mediaMapper: MediaMapper,
    @Autowired val mediaDimensionService: MediaDimensionService,
    @Autowired val mediaFeatureService: MediaFeatureService,
    @Autowired val categoryCacheService: CategoryCacheService
) {

    @ApiOperation("新建介质")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun addMedia(@Valid @RequestBody req: CreateNameInput): CreatedResponse {
        val media = mediaService.save(Media(req.nameCN, req.nameEN).also { it.image = req.image })
        categoryCacheService.refresh()
        return RestResponse.created(media)
    }

    @ApiOperation("新建维度")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping("/dimension")
    fun addMediaDimension(@Valid @RequestBody req: CreateDimensionInput): CreatedResponse {
        val mediaDimension = mediaDimensionService.add(req)
        categoryCacheService.refresh()
        return RestResponse.created(mediaDimension)
    }

    @ApiOperation("新建特性")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping("/feature")
    fun addMediaFeature(@Valid @RequestBody req: CreateMediaFeatureInput): CreatedResponse {
        val mediaFeature = mediaFeatureService.add(req)
        categoryCacheService.refresh()
        return RestResponse.created(mediaFeature)
    }

    @ApiOperation("修改介质分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateMedia(@PathVariable id: UUID, @Valid @RequestBody input: CreateNameInput) {
        val media = mediaService.findOrThrow(id)
        categoryCacheService.refresh()
        mediaService.update(media, input)
    }

    @ApiOperation("修改维度分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/dimension/{id}")
    fun updateMediaDimension(@PathVariable id: UUID, @Valid @RequestBody input: CreateDimensionInput) {
        val mediaDimension = mediaDimensionService.findOrThrow(id)
        categoryCacheService.refresh()
        mediaDimensionService.updateDimension(mediaDimension, input)
    }

    @ApiOperation("修改特性分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/feature/{id}")
    fun updateMediaFeature(@PathVariable id: UUID, @Valid @RequestBody input: CreateMediaFeatureInput) {
        val mediaFeature = mediaFeatureService.findOrThrow(id)
        categoryCacheService.refresh()
        mediaFeatureService.updateFeature(mediaFeature, input)
    }

    @ApiOperation("删除介质")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun removeMedia(@PathVariable id: UUID) {
        mediaService.deleteAndCheck(id)
    }

    @ApiOperation("删除维度")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/dimension/{id}")
    fun removeMediaDimension(@PathVariable id: UUID) {
        mediaDimensionService.deleteAndCheck(id)
    }

    @ApiOperation("删除特性")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/feature/{id}")
    fun removeMediaFeature(@PathVariable id: UUID) {
        mediaFeatureService.deleteAndCheck(id)
    }

    @ApiOperation("获取所有介质")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    @Transactional
    fun getMedias() = mediaService
        .findAll(Sort.by(Sort.Direction.DESC, "weight"))
        .map(mediaMapper::mediaToMediaOutput)

    @ApiOperation("更改介质排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): List<MediaOutput> {
        categoryCacheService.refresh()
        return mediaService.sort(ids).map(mediaMapper::mediaToMediaOutput)
    }

    @ApiOperation("更改维度排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/dimension")
    fun dimensionChangeLocation(@RequestBody ids: List<UUID>): List<DimensionOutput> {
        categoryCacheService.refresh()
        return mediaDimensionService.sort(ids).map(mediaMapper::dimensionToDimensionOutput)
    }

    @ApiOperation("更改特性排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/feature")
    fun featureChangeLocation(@RequestBody ids: List<UUID>): List<FeatureOutput> {
        categoryCacheService.refresh()
        return mediaFeatureService.sort(ids).map(mediaMapper::featureToFeatureOutput)
    }
}
