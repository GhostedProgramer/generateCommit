package com.musicbible.load

import com.musicbible.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import org.springframework.stereotype.Component

/**
 * 初始化管理员数据
 *
 * @author yu
 */
@Component
class AdminLoader(
    @Autowired val userService: UserService
) : Loader {

    private val logger = LoggerFactory.getLogger(AdminLoader::class.java)

    override fun load() {
        logger.info("Create super admin: 'root', password is 'password'")
        userService.createRoot("root", "password")
    }

    override fun isLoad(): Boolean {
        return userService.existsByUserName("root")
    }
}
