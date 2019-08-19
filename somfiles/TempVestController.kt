package com.musicbible.controller

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.vest.VestMapper
import com.musicbible.mapper.vest.VestOutput
import com.musicbible.mapper.vest.VestPassInput
import com.musicbible.model.VestStatus
import com.musicbible.service.vest.TempVestService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v0/vest")
@Api("/api/v0/vest", tags = ["M 马甲"], description = "Vest")
class TempVestController(
    @Autowired val tempVestService: TempVestService,
    @Autowired val vestMapper: VestMapper
) {

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('MANAGE_VEST')")
    @GetMapping
    fun list(
        @RequestParam("q") q: String?,
        @RequestParam("status") status: VestStatus?,
        pageQuery: PageQuery
    ): PageResponse<VestOutput> {
        val list = tempVestService.list(q, status, pageQuery.defaultSortByCreateAt())
        val map = list.map {
            val output = vestMapper.toVestOutput(it)
            if (it.status == VestStatus.USED) {
                output.avatar = it.reliableAvatar.orEmpty()
            }
            output
        }
        return map.let(RestResponse::page)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_VEST')")
    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: UUID) {
        tempVestService.remove(id)
    }

    @ApiOperation("通过")
    @PreAuthorize("hasAuthority('MANAGE_VEST')")
    @PutMapping("/{id}/pass")
    fun pass(@PathVariable id: UUID, @RequestBody input: VestPassInput) {
        tempVestService.pick(id, input.objectKey)
    }
}
