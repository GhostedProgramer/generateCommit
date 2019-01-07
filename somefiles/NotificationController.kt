package com.musicbible.controller

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.notification.NotificationInput
import com.musicbible.mapper.notification.NotificationMapper
import com.musicbible.mapper.notification.NotificationOutput
import com.musicbible.mapper.notification.NotificationQueryInput
import com.musicbible.service.NotificationService
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/notification")
@Api(value = "/api/v0/notification", tags = ["T 通知管理"], description = "Notification")
class NotificationController(
    @Autowired val notificationService: NotificationService,
    @Autowired val userService: UserService,
    @Autowired val notificationMapper: NotificationMapper
) {

    @ApiOperation(value = "后台创建系统通知")
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_PUSH')")
    @PostMapping("/system")
    fun create(@Valid @RequestBody input: NotificationInput) {
        notificationService.createSystem(input.title, input.content)
    }

    @ApiOperation(value = "后台分页查询通知")
    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_PUSH')")
    fun page(@Valid input: NotificationQueryInput): PageResponse<NotificationOutput> {
        return notificationService.backendList(input.title, input.defaultSortByCreateAt())
            .map(notificationMapper::toOutput)
            .let(RestResponse::page)
    }
}
