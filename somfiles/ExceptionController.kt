package com.musicbible.controller

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.exception.ExceptionDetailOutput
import com.musicbible.mapper.exception.ExceptionListInput
import com.musicbible.mapper.exception.ExceptionListOutput
import com.musicbible.mapper.exception.ExceptionMapper
import com.musicbible.model.Exception
import com.musicbible.service.ExceptionService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import javax.validation.Valid

@RestController
@RequestMapping("api/v0/exception")
@Api("api/v0/exception", tags = ["Y 异常"], description = "Exception")
class ExceptionController(
    val exceptionService: ExceptionService<Exception>,
    val exceptionMapper: ExceptionMapper
) {

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('MANAGE_LOGGING')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): ExceptionDetailOutput {
        val exception = exceptionService.findOrThrow(id)
        return exceptionMapper.toDetailOutput(exception)
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('MANAGE_LOGGING')")
    @GetMapping
    fun list(@Valid input: ExceptionListInput): PageResponse<ExceptionListOutput> {
        return exceptionService.findAll(input.defaultSort("-time"))
            .map(exceptionMapper::toExceptionListOutput)
            .let(RestResponse::page)
    }
}
