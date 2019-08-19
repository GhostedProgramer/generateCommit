package com.musicbible.service

import com.musicbible.model.Setting
import com.musicbible.repository.SettingRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.Serializable

interface SettingService : SettingRepository {
    fun <T : Serializable> set(key: String, value: T)
    fun <T : Serializable> get(key: String): T?
}

@Service
class SettingServiceImpl(
    @Autowired val settingRepository: SettingRepository
) : SettingService, SettingRepository by settingRepository {

    override fun <T : Serializable> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return findByKey(key)?.value as T?
    }

    override fun <T : Serializable> set(key: String, value: T) {
        val setting = findByKey(key) ?: Setting()
        setting.key = key
        setting.value = value
        save(setting)
    }
}

