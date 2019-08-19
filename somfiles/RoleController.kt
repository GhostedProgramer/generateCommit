package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.constant.PrivilegeEnum
import com.musicbible.mapper.admin.RoleOutput
import com.musicbible.mapper.role.CreateOrUpdateRoleInput
import com.musicbible.mapper.role.PrivilegeOutput
import com.musicbible.mapper.role.RoleDetailOutput
import com.musicbible.mapper.role.RoleMapper
import com.musicbible.model.Privilege
import com.musicbible.model.Role
import com.musicbible.model.User
import com.musicbible.security.UserOrThrow
import com.musicbible.service.RoleService
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v0/admin/role")
@Api(value = "/api/v0/admin/role", tags = ["J 角色管理"], description = "Role")
class RoleController(
    @Autowired val roleService: RoleService,
    @Autowired val userService: UserService,
    @Autowired val roleMapper: RoleMapper
) {

    @GetMapping
    @ApiOperation(value = "获取所有角色")
    @PreAuthorize("hasAuthority('MANAGE_ROLE') or hasAuthority('CREATE_ADMIN')")
    fun get(): List<RoleOutput> {
        return roleService.findAll().map {
            val output = roleMapper.toRoleOutput(it)
            output.syncCount(userService::countByRole)
            output
        }
    }

    @GetMapping("/privilege/all")
    @PreAuthorize("hasAuthority('MANAGE_ROLE')")
    @ApiOperation(value = "获取所有权限")
    fun getAllPrivileges(): List<PrivilegeOutput> {
        return PrivilegeEnum.values()
            .sorted()
            .map {
                PrivilegeOutput.from(it)
            }
    }

    @GetMapping("/privileges")
    @PreAuthorize("hasAuthority('MANAGE_ROLE') or hasAuthority('CREATE_ADMIN')")
    @ApiOperation(value = "获取角色组对应的权限")
    fun getPrivilegesOfRoles(@RequestParam roleIds: List<UUID>): List<PrivilegeOutput> {
        val ids = roleIds.distinct().toMutableList()
        return roleService.findAllById(ids)
            .map(Role::privileges).flatten()
            .map(Privilege::name).distinct()
            .sorted()
            .map {
                PrivilegeOutput.from(it)
            }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_ROLE')")
    @ApiOperation(value = "刪除角色")
    fun delete(@PathVariable id: UUID) {
        roleService.deleteOrThrow(id)
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_ROLE')")
    @ApiOperation(value = "角色详情")
    fun detail(@PathVariable id: UUID): RoleDetailOutput {
        val role = roleService.findOrThrow(id)
        val detailOutput = role.let(roleMapper::toRoleDetailOutput)
        detailOutput.privileges = role.privileges.map(Privilege::name).map {
            PrivilegeOutput.from(it)
        }
        return detailOutput
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_ROLE')")
    @ApiOperation("新建角色")
    fun create(@RequestBody @Validated input: CreateOrUpdateRoleInput): CreatedResponse {
        return roleService.create(input.name, input.privileges.toList())
            .let {
                RestResponse.created(it)
            }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_ROLE')")
    @ApiOperation("更新角色")
    fun update(
        @UserOrThrow user: User,
        @PathVariable id: UUID,
        @RequestBody @Validated input: CreateOrUpdateRoleInput
    ) {
        roleService.update(user, id, input.name, input.privileges)
    }
}
