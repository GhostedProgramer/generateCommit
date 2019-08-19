package com.musicbible.load

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 后台管理数据加载器
 *
 * @author yu
 */
@Component
class DefaultAdminDataLoader(
    @Autowired val adminLoader: AdminLoader,
    @Autowired val privilegeLoader: PrivilegeLoader,
    @Autowired val sayingLoader: SayingLoader
) : Loader {

    /**
     * the load order must be fixed
     */
    override fun load() {
        sayingLoader.load()
        privilegeLoader.load()
        adminLoader.load()
    }

    override fun reload() {
        privilegeLoader.reload()
    }

    override fun isLoad() = adminLoader.isLoad()
}
