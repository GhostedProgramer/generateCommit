package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.banner.BackendBannerListingOutput
import com.musicbible.mapper.banner.BannerBackendListInput
import com.musicbible.mapper.banner.BannerDetailOutput
import com.musicbible.mapper.banner.BannerMapper
import com.musicbible.mapper.banner.CreateBannerInput
import com.musicbible.mapper.banner.UpdateBannerImageInput
import com.musicbible.mapper.banner.UpdateBannerInput
import com.musicbible.model.Banner
import com.musicbible.service.BannerService
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
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/banner")
@Api(value = "/api/v0/banner", tags = ["L 轮播图"], description = "Banner")
class BannerController(
    @Autowired val bannerMapper: BannerMapper,
    @Autowired val bannerService: BannerService
) {

    @ApiOperation(
        value = "新建",
        notes = "用'通过保存方式创建'接口替代"
    )
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @PostMapping
    @Deprecated(message = "会引发直接在前台产生空Banner的问题取消该接口", replaceWith = ReplaceWith("post接口"))
    fun create(): CreatedResponse {
        val banner = bannerService.save(Banner())
        return RestResponse.created(banner.id)
    }

    @ApiOperation("通过保存方式创建")
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @PostMapping("/save")
    fun post(@Valid @RequestBody input: CreateBannerInput): CreatedResponse {
        val banner = bannerService.save(Banner().also { banner ->
            input.name?.also { banner.name = it }
            input.relatedSite?.also { banner.relatedSite = it }
            input.webImage?.also { banner.webImage = it }
            input.appImage?.also { banner.appImage = it }
        })
        return RestResponse.created(banner.id)
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @GetMapping
    fun list(@Valid input: BannerBackendListInput): PageResponse<BackendBannerListingOutput> {
        return bannerService.findList(input)
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): BannerDetailOutput {
        val banner = bannerService.findOrThrow(id)
        return bannerMapper.toDetail(banner)
    }

    @ApiOperation("修改图片")
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @PutMapping("/{id}/Image")
    fun updateWebImages(@PathVariable id: UUID, @Valid @RequestBody body: UpdateBannerImageInput) {
        val banner = bannerService.findOrThrow(id)
        bannerService.updateWebImages(banner, body)
    }

    @ApiOperation("修改基本字段")
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @PutMapping("/{id}")
    fun updateFields(@PathVariable id: UUID, @Valid @RequestBody body: UpdateBannerInput) {
        val banner = bannerService.findOrThrow(id)
        bannerService.updateFields(banner, body)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_BANNER')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        bannerService.delete(id)
    }
}
