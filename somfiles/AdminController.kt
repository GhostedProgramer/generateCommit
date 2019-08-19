package com.musicbible.controller

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.EmptyResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.boostfield.verifycodespringbootstarter.VerifyCodeService
import com.musicbible.mapper.admin.AdminMapper
import com.musicbible.mapper.admin.ChangePasswordInput
import com.musicbible.mapper.admin.CreateAdminInput
import com.musicbible.mapper.admin.PageInput
import com.musicbible.mapper.admin.UpdateAdminInput
import com.musicbible.mapper.admin.UpdateAvatar
import com.musicbible.mapper.admin.UpdateInfoInput
import com.musicbible.mapper.admin.UpdateProfile
import com.musicbible.mapper.admin.UserChangePasswordTokenOutput
import com.musicbible.mapper.auth.ChangePasswordRequest
import com.musicbible.mapper.role.PrivilegeOutput
import com.musicbible.mapper.role.RoleDetailOutput
import com.musicbible.mapper.role.RoleMapper
import com.musicbible.mapper.user.UserProfileOutput
import com.musicbible.model.Privilege
import com.musicbible.model.Role
import com.musicbible.model.User
import com.musicbible.security.CurrentUser
import com.musicbible.security.UserOrThrow
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.annotations.ApiIgnore
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/admin")
@Api(value = "/api/v0/admin", description = "Admin", tags = ["Y 用户管理"])
class AdminController(
    @Autowired val adminMapper: AdminMapper,
    @Autowired val userService: UserService,
    @Autowired val verifyCodeService: VerifyCodeService,
    @Autowired val roleMapper: RoleMapper
) {
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_ADMIN')")
    @ApiOperation(value = "后台管理用户添加")
    fun create(@Valid @RequestBody input: CreateAdminInput): CreatedResponse {
        val user = userService.create(input)
        return RestResponse.created(user.id)
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ_ADMIN')")
    @ApiOperation(value = "后台管理用户分页查询")
    fun gets(@Valid input: PageInput, @Valid pageQuery: PageQuery) = userService
        .page(input.key, pageQuery.defaultSort("blocked", "-registerAt"))
        .map(adminMapper::userToUserOutput)
        .let(RestResponse::page)

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_ADMIN')")
    @ApiOperation(value = "后台管理用户单查询")
    fun get(@PathVariable id: UUID) = userService
        .findOrThrow(id)
        .let(adminMapper::userToUserOutput)


    @PutMapping("/{id}/block")
    @PreAuthorize("hasAuthority('BLOCK_ADMIN')")
    @ApiOperation(value = "封禁用户")
    fun block(@UserOrThrow user: User, @PathVariable id: UUID) {
        userService.block(user, id)
    }

    @PutMapping("/{id}/unblock")
    @PreAuthorize("hasAuthority('BLOCK_ADMIN')")
    @ApiOperation(value = "启用用户")
    fun unblock(@UserOrThrow user: User, @PathVariable id: UUID) {
        userService.unblock(user, id)
    }

    @PutMapping("/info")
    @ApiOperation(value = "修改昵称")
    fun updateNicknameAndAvatar(
        @CurrentUser user: User, @Valid @RequestBody input: UpdateInfoInput
    ) {
        userService.updateInfo(user.id, input)
    }

    @PutMapping("/avatar")
    @ApiOperation(value = "修改个人头像")
    fun updateAvatar(@CurrentUser user: User, @Valid @RequestBody input: UpdateAvatar) {
        userService.updateAvatar(user.id, input.avatar)
    }

    @PutMapping("/owner/profile")
    @ApiOperation(value = "自己修改自己个人资料")
    fun updateProfileByOwner(@CurrentUser user: User, @Valid @RequestBody input: UpdateProfile) {
        userService.updateProfileByOwner(user.id, input)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_ADMIN')")
    @ApiOperation(value = "管理用户编辑")
    fun edit(@UserOrThrow user: User, @PathVariable id: UUID, @Valid @RequestBody input: UpdateAdminInput){
        userService.updateAdmin(user, id, input)
    }

    @GetMapping("/my/privilege")
    @ApiOperation(value = "获取当前用户的权限")
    fun privileges(@ApiIgnore @CurrentUser user: User): List<PrivilegeOutput> {
        return userService.findOrThrow(user.id)
            .roles
            .map(Role::privileges).flatten()
            .map(Privilege::name).distinct()
            .sorted()
            .map {
                PrivilegeOutput.from(it)
            }
    }

    @GetMapping("/my/id")
    @ApiOperation(value = "获取当前用户的ID(弃用)")
    fun id(@CurrentUser user: User) = RestResponse.created(user.id)

    @GetMapping("/my/profile")
    @ApiOperation(value = "获取当前用户的个人资料")
    fun profile(@UserOrThrow user: User): UserProfileOutput {
        return adminMapper.toProfile(user)
    }

    @GetMapping("/my/root/validate")
    @ApiOperation(value = "判断当前用户是否是root")
    fun amIRoot(@CurrentUser user: User): Boolean = user.isRoot

    @GetMapping("/{field}/exist/{value}")
    @PreAuthorize("hasAuthority('READ_ADMIN')")
    @ApiOperation(value = "判断该{field}是否已存在, field = user_name/phone")
    fun existsField(@PathVariable field: String, @PathVariable value: String): Boolean {
        return when (field) {
            "user_name" -> userService.existsByUserName(value)
            "phone" -> userService.existsByPhone(value)
            else -> throw AppError.NotFound.default()
        }
    }

    @GetMapping("/phone")
    @ApiOperation(value = "验证手机号")
    @PreAuthorize("hasAuthority('READ_ADMIN')")
    fun findPhone(@Valid phone: String): EmptyResponse {
        return userService.getRegisterStatus(phone)?.let {
            RestResponse.ok()
        } ?: throw AppError.NotFound.default(msg = "电话号码${phone}未注册或已注销")
    }

    @GetMapping("/verify")
    @ApiOperation(value = "修改密码前验证输入信息")
    fun verify(@Valid input: ChangePasswordRequest): UserChangePasswordTokenOutput {
        /*验证手机验证码*/
        if (!verifyCodeService.verifyCode(input.code, input.phone)) {
            throw AppError.BadRequest.invalidSmsCode()
        }
        return userService.getRegisterStatusAndReturnToken(input.phone)
    }

    @PutMapping("/password")
    @ApiOperation(value = "修改用户密码")
    fun updatePassword(@Valid @RequestBody input: ChangePasswordInput) {
        userService.changePassword(input.code, input.newPassword)
    }

    @GetMapping("/{id}/roles")
    @ApiOperation(value = "获取该用户的角色")
    fun getRoles(@PathVariable id: UUID): List<RoleDetailOutput> {
        return userService.findOrThrow(id).roles
            .map(roleMapper::toRoleDetailOutput)
    }
}
