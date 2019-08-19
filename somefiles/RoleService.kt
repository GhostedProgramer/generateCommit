package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.aspect.Locked
import com.musicbible.constant.PrivilegeEnum
import com.musicbible.model.Privilege
import com.musicbible.model.Role
import com.musicbible.model.User
import com.musicbible.repository.RoleRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface RoleService : BaseService<Role>, RoleRepository {

    override val modelName: String
        get() = "角色"

    fun create(name: String, privileges: List<PrivilegeEnum>): Role

    /**
     * 如果该角色名已存在，则跳过该角色创建
     */
    fun safeCreate(name: String, privileges: List<PrivilegeEnum>)

    fun update(id: UUID, name: String, privileges: List<PrivilegeEnum>): Role

    fun refreshUserRoles(user: User, roleIds: List<UUID>)

    fun update(user: User, id: UUID, name: String, privileges: MutableList<PrivilegeEnum>)
}

@Service
@Transactional
class RoleServiceImpl(
    @Autowired val privilegeService: PrivilegeService,
    @Autowired val roleRepository: RoleRepository
) : RoleService, RoleRepository by roleRepository {

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun update(user: User, id: UUID, name: String, privileges: MutableList<PrivilegeEnum>) {
        update(id, name, privileges)
    }

    override fun refreshUserRoles(user: User, roleIds: List<UUID>) {
        val oldRoleIds = findByUsersContaining(user).map(Role::id)
        val toAdd = roleIds - oldRoleIds
        val toRemove = oldRoleIds - roleIds
        toAdd.map(this::findOrThrow).forEach {
            it.users.add(user)
            save(it)
        }
        toRemove.map(this::findOrThrow).forEach {
            it.users.remove(user)
            save(it)
        }
    }

    private val logger = LoggerFactory.getLogger(RoleServiceImpl::class.java)

    override fun safeCreate(name: String, privileges: List<PrivilegeEnum>) {
        if (findByName(name) != null) {
            logger.warn("[$name] Role is Exist, skip create!")
            return
        }
        create(name, privileges)
    }

    override fun create(name: String, privileges: List<PrivilegeEnum>): Role {
        val privilegeEntities = toPrivilegeEntities(privileges)
        logger.info("create [$name] Role")
        return save(Role(name, privilegeEntities))
    }

    override fun update(id: UUID, name: String, privileges: List<PrivilegeEnum>): Role {
        val role = findOrThrow(id)
        role.name = name
        role.privileges.clear()
        role.privileges = toPrivilegeEntities(privileges)
        return save(role)
    }

    private fun toPrivilegeEntities(privileges: List<PrivilegeEnum>): MutableList<Privilege> {
        return privileges.distinct().map {
            privilegeService.findByName(it) ?: privilegeService.save(Privilege(it))
        }.toMutableList()
    }
}
