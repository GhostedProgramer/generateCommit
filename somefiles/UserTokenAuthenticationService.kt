package com.musicbible.service

import com.musicbible.config.properties.TokenAuthenticationProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.io.Serializable
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 用户登录认证接口
 */
interface UserTokenAuthenticationService {

    /**
     * 通过accessToken认证用户，返回该用户的ID
     *
     * @param token 必须是accessToken
     * @return 用户id
     * @exception InvalidTokenException token无效（token格式错误、无法解析、为空字符等无效token）
     * @exception HadExpiredException token过期失效
     */
    fun authenticate(token: String): UUID

    /**
     * 为一个用户ID, 生成一个认证token
     *
     * @param id 用户id
     * @see AdvanceAuthenticationResponse
     */
    fun generateToken(id: UUID): AdvanceAuthenticationResponse

    /**
     * 通过refreshToken, 去刷新token过期事件，获取一个新的Token。
     * - 如果旧Token有效，则设置一小时过期，再生成一个新Token。
     * - 如果旧Token已过期，则直接生成新Token
     *
     * @exception InvalidTokenException token无效
     * @exception HadExpiredException token已失效
     * @exception RunOutTokenException TTL已用尽
     * @see AdvanceAuthenticationResponse
     */
    fun refreshToken(token: String): AdvanceAuthenticationResponse

    /**
     * 判断一个token是否过期
     *
     * @param token 可以是accessToken或freshToken
     * @return 返回true表示过期， false表示未过期
     */
    fun isTokenExpired(token: String): Boolean

    /**
     * 从缓存中移除该AccessToken, 以及跟该accessToken绑定的RefreshToken。
     * 如果token存在，则直接删除；如果不存在，则不处理。
     *
     * @param accessToken 必须是accessToken
     */
    fun remove(accessToken: String)
}

@Service
class UserTokenAuthenticationServiceImpl(
    @Autowired val redisTemplate: RedisTemplate<String, Token>,
    @Autowired var properties: TokenAuthenticationProperties
) : UserTokenAuthenticationService {
    companion object {
        const val OLD_TOKEN_KEEP_DURATION = 3600L
    }

    override fun remove(accessToken: String) {
        val key = parseJwtToken(accessToken)

        // 如果该token还存活
        redisTemplate.opsForValue().get(key)?.also {

            // 判断是accessToken
            if (it.type == accessTokenType) {
                val refreshTokenKey = it.link

                // 从redis中清除
                redisTemplate.delete(refreshTokenKey)
                redisTemplate.delete(key)
            }
        }
    }

    val logger = LoggerFactory.getLogger(UserTokenAuthenticationServiceImpl::class.java)
    val provider: UniqueJwtProvider
        get() = UniqueJwtProvider(this.properties.secret)

    val accessTokenType = 1
    val freshTokenType = 2

    /**
     * 旧Token被替换后，保留的时间./秒
     */
    val oldTokenKeepDuration: Long = OLD_TOKEN_KEEP_DURATION

    override fun authenticate(token: String): UUID {
        // 校验Token
        val key: String = parseJwtToken(token)

        // fetch Token
        val tokenValue: Token = getTokenValue(key)

        // 校验Token 类型
        if (tokenValue.type == accessTokenType) {
            return tokenValue.id
        }
        throw InvalidTokenException()
    }

    private fun getTokenValue(key: String): Token {
        return redisTemplate.opsForValue().get(key) ?: throw HadExpiredException()
    }


    override fun refreshToken(token: String): AdvanceAuthenticationResponse {
        val key = parseJwtToken(token)

        // 获取有效refreshToken
        val tokenValue: Token = getTokenValue(key)
        logger.debug("Old Token: $tokenValue")

        // 校验类型
        if (tokenValue.type == freshTokenType) {
            //1. 判断是否有刷新次数
            val ttl = tokenValue.ttl
            if (ttl < 1) {
                throw RunOutTokenException()
            }

            //2. 判断关联的AccessToken状态
            val accessKey = tokenValue.link
            if (!isKeyExpired(accessKey)) {
                // 如果AccessToken还在，则设置过期时间。
                redisTemplate.expire(accessKey, oldTokenKeepDuration, TimeUnit.SECONDS)
            }

            //3. 删除自己，并新建Token，设置ttl
            redisTemplate.delete(key)
            return buildToken(tokenValue.id, properties.access.keepExpiredTime, properties.refresh.keepExpiredTime, ttl - 1)
        }
        throw InvalidTokenException()
    }

    override fun isTokenExpired(token: String): Boolean {
        val key = parseJwtToken(token)
        return isKeyExpired(key)
    }

    override fun generateToken(id: UUID): AdvanceAuthenticationResponse {
        val ttl = properties.ttl
        val refreshExpired = properties.refresh.initExpiredTime
        val accessExpired = properties.access.initExpiredTime
        logger.debug("""
            Token config:
            ttl: $ttl
            refreshExpired: $refreshExpired
            accessExpired: $accessExpired
        """.trimIndent())
        return buildToken(id, accessExpired, refreshExpired, ttl)
    }

    fun buildToken(id: UUID, accessExpired: Long, refreshExpired: Long, ttl: Int): AdvanceAuthenticationResponse {
        val accessKeyAndValue = buildAccessTokenValue(id)
        val refreshTokenValue = buildRefreshTokenValue(id, accessKeyAndValue.first, ttl)
        accessKeyAndValue.second.link = refreshTokenValue.first

        logger.debug("""
            accessToken: $accessKeyAndValue
            refreshToken: $refreshTokenValue
            expired: $accessExpired
        """.trimIndent())

        val now = ZonedDateTime.now()

        // 保存AccessToken
        redisTemplate.opsForValue()
            .set(
                accessKeyAndValue.first,
                accessKeyAndValue.second,
                accessExpired,
                TimeUnit.SECONDS
            )

        // 保存RefreshToken
        redisTemplate.opsForValue()
            .set(
                refreshTokenValue.first,
                refreshTokenValue.second,
                refreshExpired,
                TimeUnit.SECONDS
            )

        return AdvanceAuthenticationResponse(
            accessToken = provider.build(accessKeyAndValue.first),
            refreshToken = provider.build(refreshTokenValue.first),
            expireAt = now.plusSeconds(accessExpired),
            refreshExpireAt = now.plusSeconds(refreshExpired)
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun parseJwtToken(token: String): String {
        val key: String
        try {
            key = provider.parse(token)
        } catch (e: RuntimeException) {
            throw InvalidTokenException()
        }
        return key
    }

    private fun buildRefreshTokenValue(id: UUID, accessKey: String, ttl: Int): Pair<String, Token> {
        return provider.generate() to Token(
            id = id,
            type = freshTokenType,
            ttl = ttl,
            link = accessKey
        )
    }

    private fun buildAccessTokenValue(id: UUID): Pair<String, Token> {
        return provider.generate() to Token(
            id = id,
            type = accessTokenType
        )
    }

    private fun isKeyExpired(key: String): Boolean {
        return redisTemplate.opsForValue().get(key) == null
    }
}

class Token(
    var id: UUID = UUID(0, 0),
    var type: Int = 0,
    var ttl: Int = 0,
    var link: String = ""
) : Serializable {
    override fun toString(): String {
        return "Token(id=$id, type=$type, ttl=$ttl, link='$link')"
    }
}

data class AdvanceAuthenticationResponse(
    val accessToken: String,
    val refreshToken: String,
    val expireAt: ZonedDateTime,
    val refreshExpireAt: ZonedDateTime,
    val tokenType: String = "Bearer"
)

/**
 * @author yu
 */
class UniqueJwtProvider(val secret: String) {
    companion object {
        const val LENGTH = 16
    }

    var length: Int = LENGTH

    fun build(key: String): String {
        return Jwts.builder()
            .setSubject(key)
            .signWith(SignatureAlgorithm.HS512, secret)
            .compact()
    }

    fun generate(): String {
        return RandomStringUtils.randomAlphabetic(length)
    }

    fun parse(token: String): String {
        val claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).body
        return claims.subject
    }
}

open class UserAuthenticationException : RuntimeException()
class InvalidTokenException : UserAuthenticationException()
class RunOutTokenException : UserAuthenticationException()
class HadExpiredException : UserAuthenticationException()
