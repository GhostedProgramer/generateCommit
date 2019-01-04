package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.saying.SayingEditInput
import com.musicbible.mapper.saying.SayingMapper
import com.musicbible.service.SayingService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.validation.Valid


@RestController
@RequestMapping("/api/v0/saying")
@Api(value = "/api/v0/saying", tags = ["M 名言"], description = "Saying")
class SayingController(
    @Autowired val sayingService: SayingService,
    @Autowired val sayingMapper: SayingMapper
) : BaseController() {

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_SAYING')")
    @ApiOperation("查詢")
    fun list(query: PageQuery) = sayingService
        .findAll(query.defaultSortByCreateAt())
        .map(sayingMapper::toList)
        .let(RestResponse::page)

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SAYING')")
    @ApiOperation("編輯")
    fun edit(@PathVariable id: UUID, @Valid @RequestBody input: SayingEditInput) {
        sayingService.update(id, input.artistId, input.images, input.contentCN, input.contentEN)
    }
}
