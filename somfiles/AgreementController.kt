package com.musicbible.controller

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.agreement.AddAgreementInput
import com.musicbible.mapper.agreement.AgreementListOutput
import com.musicbible.mapper.agreement.AgreementMapper
import com.musicbible.mapper.agreement.EditAgreementInput
import com.musicbible.service.AgreementService
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
@RequestMapping("/api/v0/agreement")
@Api("/api/v0/agreement", tags = ["X 协议"], description = "Agreement")
class AgreementController(
    @Autowired val agreementService: AgreementService,
    @Autowired val agreementMapper: AgreementMapper
) {

    @ApiOperation("获取协议")
    @PreAuthorize("hasAuthority('MANAGE_AGREEMENT')")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): AgreementListOutput = agreementService
        .findOrThrow(id)
        .let(agreementMapper::toAgreementList)

    @ApiOperation("增加协议")
    @PreAuthorize("hasAuthority('MANAGE_AGREEMENT')")
    @PostMapping
    fun add(@Valid @RequestBody input: AddAgreementInput) =
        RestResponse.created(agreementService.create(input.title, input.content))

    @ApiOperation("删除协议")
    @PreAuthorize("hasAuthority('MANAGE_AGREEMENT')")
    @DeleteMapping("/{id}")
    fun rm(@PathVariable id: UUID) {
        agreementService.delete(id)
    }

    @ApiOperation("编辑协议")
    @PreAuthorize("hasAuthority('MANAGE_AGREEMENT')")
    @PutMapping("/{id}")
    fun edit(@PathVariable id: UUID, @Valid @RequestBody input: EditAgreementInput) {
        agreementService.edit(id, input.title, input.content)
    }

    @ApiOperation("协议列表")
    @PreAuthorize("hasAuthority('MANAGE_AGREEMENT')")
    @GetMapping
    fun list(@Valid pageQuery: PageQuery): PageResponse<AgreementListOutput> {
        return agreementService
            .findAll(pageQuery.defaultSortByCreateAt())
            .map(agreementMapper::toAgreementList)
            .let(RestResponse::page)
    }

    @ApiOperation("标题是否存在")
    @PreAuthorize("hasAuthority('MANAGE_AGREEMENT')")
    @GetMapping("/title/{title}/exist")
    fun askTitle(@PathVariable title: String): Boolean =
        agreementService.findByTitle(title) != null
}
