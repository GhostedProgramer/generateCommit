package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.literature.BackendLiteratureListingOutput
import com.musicbible.mapper.literature.CreateLiteratureInput
import com.musicbible.mapper.literature.LiteratureBackendListInput
import com.musicbible.mapper.literature.LiteratureDetailOutput
import com.musicbible.mapper.literature.LiteratureMapper
import com.musicbible.mapper.literature.UpdateLiteratureImageInput
import com.musicbible.mapper.literature.UpdateLiteratureInput
import com.musicbible.model.Literature
import com.musicbible.service.LiteratureService
import com.musicbible.service.ReleaseService
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
@RequestMapping("/api/v0/literature")
@Api(value = "/api/v0/literature", tags = ["Y 音乐圣经微信公众号文章"], description = "Literature")
class LiteratureController(
    @Autowired val literatureMapper: LiteratureMapper,
    @Autowired val literatureService: LiteratureService,
    @Autowired val releaseService: ReleaseService
) {
    @ApiOperation(
        value = "新建",
        notes = "用'通过保存方式创建'接口替代"
    )
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @PostMapping
    @Deprecated(message = "会引发直接在前台产生空文章的问题取消该接口", replaceWith = ReplaceWith("save接口"))
    fun create(): CreatedResponse {
        val literature = literatureService.save(Literature())
        return RestResponse.created(literature.id)
    }

    @ApiOperation("通过保存方式创建")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @PostMapping("/save")
    fun save(@Valid @RequestBody input: CreateLiteratureInput): CreatedResponse {
        val literature = literatureService.save(Literature().also { literature ->
            literature.name = input.name
            literature.relatedSite = input.relatedSite
            input.image?.also { literature.image = it }

            literature.releases.clear()
            input.releaseIds.forEach {
                literature.releases.add(releaseService.findExistsOrThrow(it))
            }
            literature.releaseCount = literature.releases.size.toLong()
        })
        return RestResponse.created(literature.id)
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @GetMapping
    fun list(@Valid input: LiteratureBackendListInput): PageResponse<BackendLiteratureListingOutput> {
        return literatureService.findList(input)
    }

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): LiteratureDetailOutput {
        val literature = literatureService.findOrThrow(id)
        return literatureMapper.toDetail(literature)
    }

    @ApiOperation("修改图片")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @PutMapping("/{id}/Image")
    fun updateImages(@PathVariable id: UUID, @Valid @RequestBody body: UpdateLiteratureImageInput) {
        val literature = literatureService.findOrThrow(id)
        literatureService.updateImages(literature, body)
    }

    @ApiOperation("修改基本字段")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @PutMapping("/{id}")
    fun updateFields(@PathVariable id: UUID, @Valid @RequestBody body: UpdateLiteratureInput) {
        val literature = literatureService.findOrThrow(id)
        literatureService.updateFields(literature, body)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        literatureService.findAndDelete(id)
    }

    @ApiOperation("更新关联唱片")
    @PreAuthorize("hasAuthority('MANAGE_OFFICIAL_ARTICLE')")
    @PutMapping("/{id}/release")
    fun updateRelateRelease(@PathVariable id: UUID, @Valid @RequestBody releaseIds: MutableSet<UUID>) {
        literatureService.updateRelateRelease(id, releaseIds)
    }
}
