package com.musicbible.controller

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.user.UserBackendDetail
import com.musicbible.mapper.user.UserBackendListOutput
import com.musicbible.mapper.user.UserListInput
import com.musicbible.mapper.user.UserMapper
import com.musicbible.model.QUser
import com.musicbible.model.User
import com.musicbible.security.UserOrThrow
import com.musicbible.service.UserService
import com.querydsl.core.types.dsl.BooleanExpression
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/user")
@Api(value = "/api/v0/user", description = "User", tags = ["Y 用户管理"])
class UserController(
    @Autowired val userService: UserService,
    @Autowired val userMapper: UserMapper
) {
    val qUser: QUser = QUser.user

    @ApiOperation(value = "用户详情")
    @PreAuthorize("hasAuthority('MANAGE_USER')")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): UserBackendDetail {
        return userService.findOrThrow(id)
            .let(userMapper::toBackendDetail)
    }

    @ApiOperation("用户列表")
    @PreAuthorize("hasAuthority('MANAGE_USER')")
    @GetMapping
    fun list(@Valid input: UserListInput): PageResponse<UserBackendListOutput> {
        // 过滤马甲账号
        var criteria: BooleanExpression = qUser.isVest.isFalse
        input.q?.also {
            when (input.key) {
                "phone" -> criteria = criteria.and(qUser.phone.like("%$it%"))
                "nickName" -> criteria = criteria.and(qUser.nickName.like("%$it%"))
            }
        }
        return userService.findAll(criteria, input.pageable())
            .map(userMapper::toBackendList)
            .let(RestResponse::page)
    }

    @PutMapping("/{id}/block")
    @PreAuthorize("hasAuthority('MANAGE_USER')")
    @ApiOperation(value = "封禁用户")
    fun block(@UserOrThrow user: User, @PathVariable id: UUID) {
        userService.frontBlock(user, id)
    }

    @PutMapping("/{id}/unblock")
    @PreAuthorize("hasAuthority('MANAGE_USER')")
    @ApiOperation(value = "启用用户")
    fun unblock(@UserOrThrow user: User, @PathVariable id: UUID) {
        userService.frontUnblock(user, id)
    }

    @PutMapping("/{id}/on/vest")
    @PreAuthorize("hasAuthority('MANAGE_USER')")
    @ApiOperation(value = "启动马甲")
    fun onWrapVest(@UserOrThrow user: User, @PathVariable id: UUID) {
        userService.setWrapVest(user, id, true)
    }

    @PutMapping("/{id}/off/vest")
    @PreAuthorize("hasAuthority('MANAGE_USER')")
    @ApiOperation(value = "关闭马甲")
    fun offWrapVest(@UserOrThrow user: User, @PathVariable id: UUID) {
        userService.setWrapVest(user, id, false)
    }
}
