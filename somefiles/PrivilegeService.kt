package com.musicbible.service

import com.musicbible.constant.PrivilegeEnum
import com.musicbible.model.Privilege
import com.musicbible.repository.PrivilegeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface PrivilegeService : PrivilegeRepository {
    fun create(privilege: PrivilegeEnum): Privilege

    /**
     * 将PrivilegeEnum中定义的权限持久化到DB
     */
    fun load()

    /**
     * 重新将PrivilegeEnum中定义的权限持久化到DB，
     * 用户PrivilegeEnum中的定义有变动，需要重新同步到DB。
     */
    fun reload()
}

@Service
@Transactional
class PrivilegeServiceImpl(
    @Autowired val privilegeRepository: PrivilegeRepository
) : PrivilegeService, PrivilegeRepository by privilegeRepository {
    override fun reload() = load()

    override fun load() {
        for (it in PrivilegeEnum.values()) {
            if (!existsByName(it)) {
                save(Privilege(it))
            }
        }
    }

    override fun create(privilege: PrivilegeEnum): Privilege {
        return privilegeRepository.save(Privilege(privilege))
    }
}
