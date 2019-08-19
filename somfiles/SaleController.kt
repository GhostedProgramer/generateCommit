package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.sale.*
import com.musicbible.model.DocumentStatus
import com.musicbible.model.PackageCondition
import com.musicbible.model.ReleaseCondition
import com.musicbible.model.Sale
import com.musicbible.model.SalePlatformEnum
import com.musicbible.service.SaleService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/sale")
@Api(value = "/api/v0/sale", tags = ["Z 在售唱片"], description = "Sale")
class SaleController(
    @Autowired val saleService: SaleService,
    @Autowired val saleMapper: SaleMapper
) {

    @ApiOperation("新建空在售唱片")
    @PostMapping
    fun create(): CreatedResponse {
        val sale = saleService.save(Sale())
        return RestResponse.created(sale.id)
    }

    @ApiOperation("详情")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): SaleDetailOutput {
        val sale = saleService.findExistsOrThrow(id)
        return saleMapper.toOutput(sale).also {
            it.status = when (sale.onSale) {
                true -> DocumentStatus.PUBLISHED
                false -> DocumentStatus.DRAFT
            }
        }
    }

    @ApiOperation("列表")
    @ApiImplicitParam(
        name = "sort",
        value = "createdAt:创建时间,updatedAt:最后更新时间",
        allowableValues = "createdAt,updatedAt")
    @GetMapping
    fun list(
        @Validated pageQuery: PageQuery,
        @RequestParam(required = false) releaseCondition: ReleaseCondition?,
        @RequestParam(required = false) packageCondition: PackageCondition?,
        @RequestParam(required = false) platformEnum: SalePlatformEnum?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) key: Key?
    ): PageResponse<BackendSaleListItemOutput> {
        val output = saleService.backendList(pageQuery, releaseCondition, packageCondition, platformEnum, q, key)
            .map(saleMapper::toBackendSaleListItemOutput)
        output.forEach {
            it.status = when (it.onSale) {
                true -> DocumentStatus.PUBLISHED
                false -> DocumentStatus.DRAFT
            }
        }
        return output.let(RestResponse::page)
    }

    @ApiOperation("上架")
    @PutMapping("/{id}/publish")
    fun onSale(@PathVariable id: UUID) {
        val sale = saleService.findExistsOrThrow(id)
        saleService.onSale(sale)
    }

    @ApiOperation("下架")
    @PutMapping("/{id}/suppress")
    fun unSale(@PathVariable id: UUID) {
        val sale = saleService.findExistsOrThrow(id)
        saleService.unSale(sale)
    }

    @ApiOperation("删除")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        val sale = saleService.findExistsOrThrow(id)
        saleService.softDelete(sale)
    }

    @ApiOperation("修改图片")
    @PutMapping("/{id}/images")
    fun updateImages(@PathVariable id: UUID, @Valid @RequestBody images: Array<String>) {
        val sale = saleService.findExistsOrThrow(id)
        saleService.updateImages(sale, images)
    }

    @ApiOperation("修改关联的唱片")
    @PutMapping("/{id}/release")
    fun updateRelease(@PathVariable id: UUID, @Valid @RequestBody input: UpdateSaleReleaseInput) {
        saleService.updateRelease(id, input.releaseId)
    }

    @ApiOperation("修改唱片品相/名称/描述/价格/渠道")
    @PutMapping("/{id}/saleUpdate")
    fun saleUpdate(@PathVariable id: UUID, @Valid @RequestBody saleInput: SaleInput) {
        val sale = saleService.findExistsOrThrow(id)
        saleService.updateFields(sale, saleInput)
    }
}
