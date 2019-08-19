package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.SoftDeletableService
import com.musicbible.aspect.Locked
import com.musicbible.event.AppVersionNotificationEvent
import com.musicbible.mapper.appversion.AppVersionInput
import com.musicbible.model.AppVersion
import com.musicbible.model.AppVersionSetting
import com.musicbible.model.ClientType
import com.musicbible.model.QAppVersion
import com.musicbible.repository.AppVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface AppVersionService : SoftDeletableService<AppVersion>, AppVersionRepository {
    override val modelName: String
        get() = "版本管理"

    fun page(clientType: ClientType?, pageable: Pageable): Page<AppVersion>
    fun create(clientType: ClientType, version: String, changeLog: String, downloadUrl: String): AppVersion
    fun update(id: UUID, appVersionInput: AppVersionInput): AppVersion
    fun setAppVersionSetting(settingType: AppVersionSetting, appVersion: AppVersion)
    fun getAppVersionSetting(appVersionSetting: AppVersionSetting): String
    fun clearLastSupport(appVersionSetting: AppVersionSetting)
    fun delete(id: UUID)
}

@Service
@Transactional
class AppVersionServiceImpl(
    @Autowired val appVersionRepository: AppVersionRepository,
    @Autowired val settingService: SettingService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : AppVersionService, AppVersionRepository by appVersionRepository {
    private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

    override fun page(clientType: ClientType?, pageable: Pageable): Page<AppVersion> {
        val qAppVersion: QAppVersion = QAppVersion.appVersion
        var predicate = qAppVersion.deleted.isFalse
        clientType?.also {
            predicate = predicate.and(qAppVersion.clientType.eq(clientType))
        }
        return findAll(predicate, pageable)
    }

    @Locked("%{#id}")
    override fun delete(id: UUID) {
        softDelete(id)
    }

    override fun create(clientType: ClientType, version: String, changeLog: String, downloadUrl: String): AppVersion {
        val appVersion = save(AppVersion(clientType, version, changeLog, downloadUrl))
        eventPublisher.publishEvent(AppVersionNotificationEvent(appVersion))
        return appVersion
    }

    @Locked("%{#id}")
    override fun update(id: UUID, appVersionInput: AppVersionInput): AppVersion {
        return findOrThrow(id).also {
            /**
             * 从逻辑上来看,客户端的类型在创建时就应该是确定的
             * 而不应该有修改的可能性
             */
            if (it.clientType != appVersionInput.clientType) {
                throw AppError.BadRequest.clientTypeNotAllowModify()
            } else {
                it.version = appVersionInput.version
                it.changeLog = appVersionInput.changeLog
                save(it)
            }
        }
    }

    @Locked("%{#appVersion.id}")
    override fun setAppVersionSetting(settingType: AppVersionSetting, appVersion: AppVersion) {
        setSetting(settingType.toString(), appVersion.version)
    }

    override fun getAppVersionSetting(appVersionSetting: AppVersionSetting): String {
        val setting = getSetting(appVersionSetting.toString())
        when {
            setting.isNullOrBlank() -> throw AppError.BadRequest.default(msg = "请先设置$appVersionSetting")
            setting == AppVersionSetting.UNDEFINED.toString() -> throw AppError.BadRequest.default(msg = "请先设置$appVersionSetting")
            else -> return setting
        }
    }

    override fun clearLastSupport(appVersionSetting: AppVersionSetting) {
        initSetting(appVersionSetting.toString())
    }

    fun setSetting(key: String, value: String) {
        return settingService.set(key, value)
    }

    fun getSetting(key: String): String? {
        return settingService.get(key)
    }

    fun initSetting(key: String) {
        settingService.set(key, AppVersionSetting.UNDEFINED.toString())
    }
}
