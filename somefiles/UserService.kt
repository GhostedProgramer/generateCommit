package com.musicbible.service


import com.boostfield.extension.string.toUUID
import com.boostfield.spring.auth.JwtProvider
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.boostfield.spring.service.AbstractUserService
import com.google.gson.Gson
import com.musicbible.aspect.Locked
import com.musicbible.config.properties.AppProperties
import com.musicbible.config.properties.CipherConfig
import com.musicbible.mapper.admin.CreateAdminInput
import com.musicbible.mapper.admin.CreateAdminV1Input
import com.musicbible.mapper.admin.UpdateAdminInput
import com.musicbible.mapper.admin.UpdateAdminV1Input
import com.musicbible.mapper.admin.UpdateInfoInput
import com.musicbible.mapper.admin.UpdateProfile
import com.musicbible.mapper.admin.UserChangePasswordTokenOutput
import com.musicbible.mapper.admin.UserCodeOutput
import com.musicbible.mapper.user.AppWechatinfoInput
import com.musicbible.model.Artist
import com.musicbible.model.Collect
import com.musicbible.model.GenderEnum
import com.musicbible.model.Preference
import com.musicbible.model.QUser
import com.musicbible.model.Recording
import com.musicbible.model.Release
import com.musicbible.model.Role
import com.musicbible.model.User
import com.musicbible.model.Video
import com.musicbible.model.Work
import com.musicbible.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

interface UserService : AbstractUserService<User>, UserRepository {
    override val modelName: String
        get() = "User"

    fun getAuthorities(id: UUID): List<GrantedAuthority>
    fun createRoot(userName: String, passWord: String): User
    fun create(userName: String, passWord: String): User
    fun createAdmin(userName: String, passWord: String): User
    fun create(input: CreateAdminInput): User

    /**
     * 新增一个后台用户。即可登录前台，也可登录后台。
     * isAdmin的值为True。
     *
     * @param input.phone 必须是未注册过的帐号
     */
    fun create(input: CreateAdminV1Input): User

    /**
     * 新建一个后台用户，且通过手机号关联前台用户。被关联的用户必须是已存在且未被关联过的。
     * 关联成功后，将该用户的isAdmin字段设置为True。
     */
    fun association(input: CreateAdminV1Input): User

    /**
     * 后台用户编辑V1
     */
    fun updateAdmin(id: UUID, input: UpdateAdminV1Input)

    fun createOrUpdate(input: AppWechatinfoInput, phone: String): User
    fun findOrCreate(phone: String): User
    fun updateInfo(id: UUID, input: UpdateInfoInput)
    fun updateAvatar(id: UUID, avatar: String)
    fun updatePhone(id: UUID, phone: String)
    fun updatePreference(user: User, preference: Preference)
    fun getPreference(user: User): Preference
    fun unbindWechat(user: User)
    fun updatePassword(id: UUID, oldPassword: String, newPassword: String)
    fun page(key: String?, pageable: Pageable): Page<User>
    fun findByUnionidAndUpdateOpenid(unionId: String, wxOpenId: String? = null, wxMiniAppOpenId: String? = null): User?
    fun genToken(user: User, expiredAt: ZonedDateTime): String
    fun saveAppWechatinfoToRedis(json: String, expiredInMinutes: Long, prefix: String): String
    fun getAppWechatinfoFromRedis(key: String): String?
    fun updateAdmin(id: UUID, input: UpdateAdminInput)
    fun addOwnRelease(ids: Array<UUID>, user: User)
    fun deleteOwnRelease(ids: Array<UUID>, user: User)
    fun addWishRelease(ids: Array<UUID>, user: User)
    fun deleteWishRelease(ids: Array<UUID>, user: User)
    fun addRole(user: User, role: Role): User
    fun updateProfileByOwner(id: UUID, input: UpdateProfile)
    fun changePassword(code: String, password: String)
    fun getRegisterStatusAndReturnToken(phone: String): UserChangePasswordTokenOutput
    fun getRegisterStatus(phone: String): User?
    fun isAdmin(id: UUID): Boolean
    fun setPublishedCount(
        user: User,
        publishedVideos: Page<Video>,
        publishedReleases: Page<Release>
    ): User

    fun setCollectCount(
        user: User,
        collectReleases: Page<Collect>,
        collectWorks: Page<Collect>,
        collectVideos: Page<Collect>,
        collectArtists: Page<Collect>,
        collectRecordings: Page<Collect>
    ): User

    fun setPublishedLatestImage(
        publishedVideos: Page<Video>,
        publishedReleases: Page<Release>,
        user: User
    ): User

    fun setCollectLatestImage(
        collectReleases: Page<Collect>,
        collectWorks: Page<Collect>,
        collectVideos: Page<Collect>,
        collectArtists: Page<Collect>, user: User, collectRecordings: Page<Collect>
    ): User

    /**
     * 冻结前台的登录
     */
    fun frontBlock(id: UUID)

    /**
     * 解除冻结前台的登录
     */
    fun frontUnblock(id: UUID)

    /**
     * 刷新最近的登录时间
     */
    fun refreshLastLoginAt(user: User)

    /**
     * 统计某个角色下的用户数量
     */
    fun countByRole(id: UUID): Long

    /**
     * 创建一个马甲账号
     *
     * @since 2019年7月24日, PM 04:50:56
     */
    fun createVestUser(nickname: String, avatar: String, gender: GenderEnum): User

    /**
     * 将一个用户设置为马甲管理员
     *
     * @since 2019年7月24日, PM 05:02:16
     */
    fun setWrapVest(id: UUID, bool: Boolean)

    fun binWechat(user: User, input: AppWechatinfoInput)

    fun block(user: User, id: UUID)

    fun unblock(user: User, id: UUID)

    fun updateAdmin(user: User, id: UUID, input: UpdateAdminInput)

    fun frontBlock(user: User, id: UUID)

    fun frontUnblock(user: User, id: UUID)

    fun setWrapVest(user: User, id: UUID, b: Boolean)

    fun updateAdmin(user: User, id: UUID, input: UpdateAdminV1Input)

    fun updateIdCard(id: UUID, idCard: IdCard): User
}

@Service
@Transactional
class UserServiceImpl(
    @Autowired val appProperties: AppProperties,
    @Autowired val roleService: RoleService,
    @Autowired val userRepository: UserRepository,
    @Autowired val passwordEncoder: PasswordEncoder,
    @Autowired val redisTemplate: RedisTemplate<String, String>,
    @Autowired val cipherService: CipherService,
    @Autowired val cipherConfig: CipherConfig,
    @Autowired @Lazy val releaseService: ReleaseService,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired @Lazy val notificationService: NotificationService
) : UserService, UserRepository by userRepository {

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateAdmin(user: User, id: UUID, input: UpdateAdminV1Input) {
        updateAdmin(id, input)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun setWrapVest(user: User, id: UUID, b: Boolean) {
        setWrapVest(id, b)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun frontUnblock(user: User, id: UUID) {
        frontUnblock(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun frontBlock(user: User, id: UUID) {
        frontBlock(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun updateAdmin(user: User, id: UUID, input: UpdateAdminInput) {
        updateAdmin(id, input)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun unblock(user: User, id: UUID) {
        super.unblock(id)
    }

    @Locked("%{#user.id}-%{#id}")
    @Transactional
    override fun block(user: User, id: UUID) {
        super.block(id)
    }

    /**
     * 将一个用户设置为马甲管理员
     *
     * @since 2019年7月24日, PM 05:02:16
     */
    override fun setWrapVest(id: UUID, bool: Boolean) {
        val user = findOrThrow(id)
        if (user.isVest) {
            throw AppError.BadRequest.default(msg = "马甲用户不能升格成马甲管理员")
        }
        user.wrapVest = bool
        save(user)
    }

    override fun createVestUser(nickname: String, avatar: String, gender: GenderEnum): User {
        val user = User().also {
            it.nickName = nickname
            it.avatar = avatar
            it.gender = gender
            it.isVest = true
        }
        return save(user)
    }

    /**
     * 统计某个角色下的用户数量
     */
    override fun countByRole(id: UUID): Long {
        val role = roleService.findOrThrow(id)
        return userRepository.countByRolesContaining(role)
    }

    val qUser: QUser = QUser.user

    override fun refreshLastLoginAt(user: User) {
        user.lastLoginAt = ZonedDateTime.now()
        save(user)
    }

    override fun frontBlock(id: UUID) {
        val user = findOrThrow(id)
        user.frontBlocked = true
        save(user)
    }

    override fun frontUnblock(id: UUID) {
        val user = findOrThrow(id)
        user.frontBlocked = false
        save(user)
    }

    override fun updateAdmin(id: UUID, input: UpdateAdminV1Input) {
        validatePhone(input.phone)
        val user = findOrThrow(id)
        val oldPhone = user.phone
        val newPhone = input.phone
        if (!oldPhone.isNullOrEmpty()) {
            if (newPhone != oldPhone) {
                ifExistsPhoneAndThrow(newPhone)
                user.phone = newPhone
            }
        } else {
            ifExistsPhoneAndThrow(newPhone)
            user.phone = newPhone
        }
        user.avatar = input.avatar.orEmpty()
        user.nickName = input.nickName
        user.blocked = input.blocked
        roleService.refreshUserRoles(user, input.roleIds)
        save(user)
    }

    override fun association(input: CreateAdminV1Input): User {
        val phone = input.phone
        validatePhone(phone)
        val user = findByPhone(phone) ?: throw AppError.NotFound.default()
        if (user.isAdmin) {
            throw AppError.BadRequest.default(msg = "该帐号已是后台用户")
        }
        user.isAdmin = true
        user.avatar = input.avatar.orEmpty()
        user.nickName = input.nickName
        user.blocked = input.blocked
        roleService.refreshUserRoles(user, input.roleIds)
        return save(user)
    }

    override fun create(input: CreateAdminV1Input): User {
        val phone = input.phone
        validatePhone(phone)
        ifExistsPhoneAndThrow(phone)
        val user = User()
        user.userName = null
        user.blocked = input.blocked
        user.nickName = input.nickName
        user.passWord = null
        user.phone = input.phone
        user.avatar = input.avatar.orEmpty()
        user.isAdmin = true
        val save = save(user)
        roleService.refreshUserRoles(save, input.roleIds)
        return save
    }

    private fun ifExistsPhoneAndThrow(phone: String) {
        if (existsByPhone(phone)) {
            throw AppError.Conflict.default(msg = "手机号${phone}已存在")
        }
    }

    override fun isAdmin(id: UUID): Boolean {
        var isAdmin = false
        findById(id).ifPresent {
            isAdmin = it.isAdmin
        }
        return isAdmin
    }

    override fun getRegisterStatus(phone: String): User? {
        return userRepository.findByPhone(phone)
    }

    override fun getRegisterStatusAndReturnToken(phone: String): UserChangePasswordTokenOutput {
        val user = userRepository.findByPhone(phone)
        if (user != null) {
            val code = UserCodeOutput(user.id, System.currentTimeMillis() + cipherConfig.expire)
            return UserChangePasswordTokenOutput(cipherService.encrypt(code.toString()))
        } else {
            throw AppError.NotFound.default(msg = "电话号码${phone}未注册或已注销")
        }
    }

    override fun changePassword(code: String, password: String) {
        val code1 = cipherService.decrypt(code)
        val list = code1.split(",")
        if ((System.currentTimeMillis() - list[1].toLong()) > 0) {
            throw AppError.BadRequest.default(msg = "请求超时,为避免他人盗用信息更改密码,请重新认证")
        }
        val user = userRepository.findById(list[0].toUUID()).orElseThrow {
            AppError.Forbidden.default(msg = "认证信息错误")
        }
        user.passWord = passwordEncoder.encode(password)
        save(user)
    }


    @Locked("%{#id}-%{#id}")
    @Transactional
    override fun updateProfileByOwner(id: UUID, input: UpdateProfile) {
        validatePhone(input.phone)
        val user = findOrThrow(id)
        user.nickName = input.nickName
        user.phone = input.phone
        user.avatar = input.avatar
        save(user)
    }

    private fun validatePhone(phone: String) {
        if (!phone.startsWith("86")) {
            throw AppError.BadRequest.default(msg = "手机格式有误")
        }
    }

    override fun createAdmin(userName: String, passWord: String): User {
        val user = User()
        user.userName = userName
        user.passWord = passwordEncoder.encode(passWord)
        user.blocked = false
        user.isAdmin = true
        return save(user)
    }

    override fun updateAdmin(id: UUID, input: UpdateAdminInput) {
        val user = findOrThrow(id)
        if (input.modifyPassword) {
            if (input.password.isNullOrBlank()) {
                throw AppError.BadRequest.paramError("修改密码不能为空")
            }
            user.passWord = passwordEncoder.encode(input.password)
        }
        input.avatar?.let { user.avatar = it }
        input.phone?.let {
            validatePhone(it)
            user.phone = it
        }
        user.userName = input.username
        user.nickName = input.nickName
        if (input.phone != null) {
            validatePhone(input.phone!!)
            user.phone = input.phone!!
        }
        user.blocked = input.blocked
        roleService.refreshUserRoles(user, input.roleIds)
        save(user)
    }

    override fun create(input: CreateAdminInput): User {
        val phone = input.phone
        if (phone != null && phone.isNotBlank()) {
            if (existsByPhone(phone)) {
                throw AppError.Conflict.default(msg = "手机号${phone}已存在")
            }
        }
        if (existsByUserName(input.userName)) {
            throw AppError.Conflict.default(msg = "用户名${input.userName}已存在")
        }
        val user = User()
        user.userName = input.userName
        user.blocked = input.blocked
        user.nickName = input.nickName
        user.passWord = passwordEncoder.encode(input.password)
        input.phone?.let { validatePhone(it) }
        user.phone = input.phone.orEmpty()
        user.avatar = input.avatar.orEmpty()
        val persistAdmin = save(user)
        user.isAdmin = true
        roleService.refreshUserRoles(persistAdmin, input.roleIds)
        return persistAdmin
    }

    override fun create(userName: String, passWord: String): User {
        val user = User()
        user.userName = userName
        user.passWord = passwordEncoder.encode(passWord)
        return save(user)
    }


    override fun createOrUpdate(input: AppWechatinfoInput, phone: String): User {
        validatePhone(phone)
        val user = findByPhone(phone) ?: (findByWxUnionId(input.unionid) ?: User())
        user.wxOpenId = input.openid
        user.wxUnionId = input.unionid
        user.nickName = input.nickname
        user.gender = input.enumGender
        user.avatar = input.imgUrl
        user.phone = phone
        return save(user)
    }

    @Suppress("MagicNumber")
    override fun findOrCreate(phone: String): User {
        validatePhone(phone)
        val user = findByPhone(phone)
        return user ?: save(User().also {
            it.phone = phone
            it.nickName = "用户${Random().nextInt(900000) + 100000}"
        })
    }

    @Locked("%{#id}")
    override fun updateInfo(id: UUID, input: UpdateInfoInput) {
        val user = findOrThrow(id)
        input.nickName?.also { user.nickName = it }
        input.gender?.also { user.gender = it }
        input.city?.also { user.city = it }
        input.birthDay?.also { user.birthDay = it }
        input.intro?.also { user.intro = it }
        input.webImage?.also { user.webImage = it }
        input.appImage?.also { user.appImage = it }
        save(user)
    }

    @Locked("%{#id}")
    override fun updateAvatar(id: UUID, avatar: String) {
        val user = findOrThrow(id)
        user.avatar = avatar
        save(user)
        val notifications = notificationService.findAllByReceiver(user)
        notifications.forEach { it.subject["avatar"] = avatar }
        notificationService.saveAll(notifications)
    }

    @Locked("%{#id}")
    override fun updatePhone(id: UUID, phone: String) {
        validatePhone(phone)
        if (findByPhone(phone) != null) {
            throw AppError.BadRequest.userWithPhoneAlreadyExist()
        }
        val user = findOrThrow(id)
        user.phone = phone
        save(user)
    }

    @Locked("%{#user.id}")
    override fun updatePreference(user: User, preference: Preference) {
        val gson = Gson()
        user.preference = gson.toJson(preference)
        save(user)
    }

    override fun getPreference(user: User): Preference {
        val preference = user.preference
        if (preference.isNullOrEmpty()) {
            return Preference()
        }
        return Gson().fromJson(preference, Preference::class.java)
    }

    @Locked("%{#user.id}")
    override fun unbindWechat(user: User) {
        user.wxMiniAppOpenId = null
        user.wxOpenId = null
        user.wxUnionId = null
        save(user)
    }

    @Locked("%{#user.id}")
    override fun binWechat(user: User, input: AppWechatinfoInput) {
        user.wxUnionId = input.unionid
        user.wxOpenId = input.openid
        save(user)
    }

    override fun page(key: String?, pageable: Pageable): Page<User> {
        var booleanExpression = qUser.isAdmin.isTrue
        if (key != null && key.isNotEmpty()) {
            booleanExpression = booleanExpression.and(qUser.nickName.contains(key)
                .or(qUser.phone.contains(key)))
        }
        return findAll(booleanExpression, pageable)
    }

    @Locked("%{#id}")
    override fun updatePassword(id: UUID, oldPassword: String, newPassword: String) {
        val user = findOrThrow(id)
        if (passwordEncoder.matches(oldPassword, user.passWord)) {
            user.passWord = passwordEncoder.encode(newPassword)
            save(user)
        } else {
            throw AppError.Unauthorized.badCredentials()
        }
    }

    override fun createRoot(userName: String, passWord: String): User {
        val user = User()
        user.userName = userName
        user.passWord = passwordEncoder.encode(passWord)
        user.isAdmin = true
        user.isRoot = true
        user.nickName = "root"
        return save(user)
    }

    override fun findByUnionidAndUpdateOpenid(unionId: String, wxOpenId: String?, wxMiniAppOpenId: String?): User? {
        val user = findByWxUnionId(unionId)
        user?.also {
            it.wxOpenId = wxOpenId ?: it.wxOpenId
            it.wxMiniAppOpenId = wxMiniAppOpenId ?: it.wxMiniAppOpenId
        }
        return user
    }

    override fun genToken(user: User, expiredAt: ZonedDateTime): String {
        val str = JwtProvider(appProperties.auth.secret).build(user, expiredAt)
        return "Bearer ${str}"
    }

    override fun saveAppWechatinfoToRedis(json: String, expiredInMinutes: Long, prefix: String): String {
        val key = "${prefix}_${System.currentTimeMillis()}"
        redisTemplate.opsForValue()
            .set(key, json, expiredInMinutes, TimeUnit.MINUTES)
        return key
    }

    override fun getAppWechatinfoFromRedis(key: String): String? =
        redisTemplate.opsForValue().get(key)

    @Locked("%{#user.id}")
    @Transactional
    override fun addOwnRelease(ids: Array<UUID>, user: User) {
        val aUser = findOrThrow(user.id)
        aUser.ownReleases.addAll(releaseService.findAllById(ids.toList()))
        aUser.ownReleaseCount = aUser.ownReleases.size.toLong()
        save(aUser)
    }

    @Locked("%{#user.id}")
    @Transactional
    override fun deleteOwnRelease(ids: Array<UUID>, user: User) {
        val aUser = findOrThrow(user.id)
        aUser.ownReleases.removeAll(releaseService.findAllById(ids.toList()))
        aUser.ownReleaseCount = aUser.ownReleases.size.toLong()
        save(aUser)
    }

    @Locked("%{#user.id}")
    @Transactional
    override fun addWishRelease(ids: Array<UUID>, user: User) {
        val aUser = findOrThrow(user.id)
        aUser.wishReleases.addAll(releaseService.findAllById(ids.toList()))
        save(aUser)
    }

    @Locked("%{#user.id}")
    @Transactional
    override fun deleteWishRelease(ids: Array<UUID>, user: User) {
        val aUser = findOrThrow(user.id)
        aUser.wishReleases.removeAll(releaseService.findAllById(ids.toList()))
        save(aUser)
    }

    override fun addRole(user: User, role: Role): User {
        roleService.refreshUserRoles(user, listOf(role.id))
        return user
    }

    @Transactional
    override fun getAuthorities(id: UUID): List<GrantedAuthority> {
        val u = findById(id)
            .orElseThrow { AppError.NotFound.default(msg = "用户未找到") }
        return u.authorities
    }

    @Locked("%{#user.id}")
    override fun setPublishedCount(user: User, publishedVideos: Page<Video>, publishedReleases: Page<Release>): User {
        user.publishedCount = publishedVideos.totalElements + publishedReleases.totalElements
        return save(user)
    }

    @Locked("%{#user.id}")
    override fun setCollectCount(
        user: User,
        collectReleases: Page<Collect>,
        collectWorks: Page<Collect>,
        collectVideos: Page<Collect>,
        collectArtists: Page<Collect>,
        collectRecordings: Page<Collect>
    ): User {
        user.collectCount = (collectReleases.totalElements
            + collectWorks.totalElements
            + collectVideos.totalElements
            + collectArtists.totalElements
            + collectRecordings.totalElements)
        return save(user)
    }

    @Locked("%{#user.id}")
    override fun setPublishedLatestImage(publishedVideos: Page<Video>, publishedReleases: Page<Release>, user: User): User {
        val video = publishedVideos.firstOrNull()
        val release = publishedReleases.firstOrNull()
        if (video != null && release != null) {
            if (video.createdAt > release.createdAt) {
                user.actionRelatedImages["published"] = video.images
            } else {
                user.actionRelatedImages["published"] = release.images
            }
        } else if (video != null && release == null) {
            user.actionRelatedImages["published"] = video.images
        } else if (video == null && release != null) {
            user.actionRelatedImages["published"] = release.images
        } else {
            user.actionRelatedImages["published"] = arrayOf()
        }
        return save(user)
    }

    @Locked("%{#user.id}")
    override fun setCollectLatestImage(
        collectReleases: Page<Collect>,
        collectWorks: Page<Collect>,
        collectVideos: Page<Collect>,
        collectArtists: Page<Collect>,
        user: User,
        collectRecordings: Page<Collect>
    ): User {
        val collect = (collectReleases + collectWorks + collectVideos + collectArtists + collectRecordings).maxBy { it.createdAt }
        if (collect != null) {
            when (val target = targetRepositoryProvider.get(collect.targetType).findByDeletedFalseAndId(collect.targetId)) {
                is Video -> user.actionRelatedImages["collect"] = target.images
                is Artist -> user.actionRelatedImages["collect"] = target.images
                is Release -> user.actionRelatedImages["collect"] = target.images
                is Work -> Unit
                is Recording -> Unit
                else -> throw AppError.NotFound.default(msg = "$target 无法被收藏")
            }
        } else {
            user.actionRelatedImages["collect"] = arrayOf()
        }
        return save(user)
    }

    @Locked("%{#id}")
    override fun updateIdCard(id: UUID, idCard: IdCard): User {
        val user = findOrThrow(id)
        user.idCard = idCard
        return save(user)
    }
}
