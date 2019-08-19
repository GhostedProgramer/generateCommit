package com.musicbible.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

const val KEY_CATEGORY_UPDATE_TIMESTAMP = "CATEGORY_UPDATE_TIMESTAMP"

/**
 * 记录所有分类的变更时间，减少前端请求
 */
interface CategoryCacheService {
    fun refresh()
    fun getUpdateTimestamp(): Long
}

@Service("categoryCacheService")
class CategoryCacheServiceImpl(
    @Autowired val settingService: SettingService,
    @Autowired val redisTemplate: RedisTemplate<String, Long>
) : CategoryCacheService {

    private val logger = LoggerFactory.getLogger(CategoryCacheService::class.java)

    override fun refresh() {
        setTimestampAsCurrent()
        logger.info("Update CATEGORY_UPDATE_TIMESTAMP current")
    }

    override fun getUpdateTimestamp(): Long {
        var timestamp = redisTemplate.opsForValue().get(KEY_CATEGORY_UPDATE_TIMESTAMP)
        if (timestamp == null) {
            timestamp = settingService.get(KEY_CATEGORY_UPDATE_TIMESTAMP)
            timestamp?.also { redisTemplate.opsForValue().set(KEY_CATEGORY_UPDATE_TIMESTAMP, it) }
        }
        if (timestamp == null) {
            timestamp = setTimestampAsCurrent()
            logger.info("Missing CATEGORY_UPDATE_TIMESTAMP, set default to current")
        }
        return timestamp
    }

    private fun setTimestampAsCurrent(): Long {
        val timestamp = Instant.now().epochSecond
        settingService.set(KEY_CATEGORY_UPDATE_TIMESTAMP, timestamp)
        redisTemplate.opsForValue()
            .set(KEY_CATEGORY_UPDATE_TIMESTAMP, timestamp)
        return timestamp
    }
}
