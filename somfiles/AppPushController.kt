package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.apppush.AppPushBackendListInput
import com.musicbible.mapper.apppush.AppPushBackendListOutput
import com.musicbible.mapper.apppush.AppPushMapper
import com.musicbible.mapper.apppush.CreateAppPushInput
import com.musicbible.model.User
import com.musicbible.security.CurrentUser
import com.musicbible.service.AppPushService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/appPush")
@Api(value = "/api/v0/appPush", description = "appPush", tags = ["AppPush管理"])
class AppPushController(
    @Autowired val appPushService: AppPushService,
    @Autowired val appPushMapper: AppPushMapper
) {

    @ApiOperation("带条件分页查询推送记录")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_PUSH')")
    @ApiImplicitParams(
        ApiImplicitParam(name = "q", required = false)
    )
    @GetMapping
    fun list(@Valid input: AppPushBackendListInput): PageResponse<AppPushBackendListOutput> {
        return appPushService.backendList(input)
            .map(appPushMapper::toAppPushBackendListOutput)
            .let(RestResponse::page)
    }

    @ApiOperation("创建一个appPush")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_PUSH')")
    @PostMapping
    fun create(@CurrentUser user: User, @Valid @RequestBody input: CreateAppPushInput): CreatedResponse {
        val appPush = appPushService.create(input)
        return RestResponse.created(appPush)
    }

    @ApiOperation(
        value = "删除appPush",
        notes = "需保证删除时推送还未发送,否则抛出400"
    )
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_PUSH')")
    @DeleteMapping("{id}")
    fun delete(@PathVariable id: UUID) {
        appPushService.remove(id)
    }
}
