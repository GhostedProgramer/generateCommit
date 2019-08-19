package com.musicbible.service

import com.boostfield.spring.SpringContext
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.TimeKeepingService
import com.boostfield.umengspringbootstarter.UmengService
import com.boostfield.umengspringbootstarter.notification.android.AndroidNotification
import com.google.gson.Gson
import com.musicbible.aspect.Locked
import com.musicbible.event.SendAppPushEvent
import com.musicbible.mapper.apppush.AppPushBackendListInput
import com.musicbible.mapper.apppush.CreateAppPushInput
import com.musicbible.model.AppPush
import com.musicbible.model.DeviceType
import com.musicbible.model.DocumentSubStatus
import com.musicbible.model.PushEntityType
import com.musicbible.model.QAppPush
import com.musicbible.model.Video
import com.musicbible.repository.AppPushRepository
import com.musicbible.repository.ArtistRepository
import com.musicbible.repository.BannerRepository
import com.musicbible.repository.LiteratureRepository
import com.musicbible.repository.ReleaseRepository
import com.musicbible.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

interface AppPushService : AppPushRepository, TimeKeepingService<AppPush> {
    fun backendList(input: AppPushBackendListInput): Page<AppPush>
    fun create(input: CreateAppPushInput): AppPush
    fun <T> sendBroadcast(broadcast: Broadcast<T>)
    fun sendVideoStatusChangeUnicast(video: Video)
    fun constructEntityPushExtra(type: PushEntityType, id: UUID? = null): PushExtraField
    fun remove(id: UUID)
}

@Service
@Transactional
class AppPushServiceImpl(
    @Autowired val appPushRepository: AppPushRepository,
    @Autowired val umengService: UmengService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val quartzService: QuartzService
) : AppPushService, AppPushRepository by appPushRepository {

    override val modelName: String
        get() = "App推送"

    private val logger = LoggerFactory.getLogger(AppPushService::class.java)

    override fun backendList(input: AppPushBackendListInput): Page<AppPush> {
        val pagebale = input.defaultSort("-createdAt")
        if (input.q.isNullOrBlank().not()) {
            val appPush = QAppPush.appPush
            val predicate = appPush.title.contains(input.q!!)
                .or(appPush.content.contains(input.q!!))
            return findAll(predicate, pagebale)
        }
        return findAll(pagebale)
    }

    override fun create(input: CreateAppPushInput): AppPush {
        var appPush = AppPush()
        appPush.title = input.title
        appPush.content = input.content
        appPush.deviceTypes = input.devicesTypes.toTypedArray()
        appPush.pushEntityType = input.pushEntityType
        appPush.targetId = input.targetId
        if (input.expectSendTime != null) {
            appPush.expectSendTime = quartzService.transFromStringToZonedDateTime(input.expectSendTime!!)
            appPush.isSend = false
        } else {
            appPush.expectSendTime = ZonedDateTime.now()
            appPush.isSend = true
        }
        appPush = save(appPush)

        eventPublisher.publishEvent(SendAppPushEvent(appPush, input.expectSendTime))
        return appPush
    }

    @Locked("%{#id}")
    override fun remove(id: UUID) {
        val appPush = findOrThrow(id)
        if (appPush.expectSendTime!! < ZonedDateTime.now()) {
            throw AppError.BadRequest.default(msg = "推送已发送,不可删除")
        } else {
            deleteById(id)
            quartzService.removeJob(id.toString())
        }
    }

    @Async
    override fun <T> sendBroadcast(broadcast: Broadcast<T>) {
        broadcast.deviceTypes
            .forEach {
                when (it) {
                    DeviceType.ANDROID -> {
                        sendAndroidBroadcast(broadcast)
                    }
                    DeviceType.IOS -> {
                        sendIOSBroadcast(broadcast)
                    }
                }
            }
    }

    @Async
    override fun sendVideoStatusChangeUnicast(video: Video) {
        val status = video.subStatus!!
        logger.debug("sending video status changed:$status")
        val allows = listOf(
            DocumentSubStatus.PUBLISHED_CHECK_PASS,
            DocumentSubStatus.DRAFT_ADMIN_REFUSE,
            DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL
        )
        if (allows.contains(status).not()) {
            logger.warn("$status not allowed in sendVideoStatusChangeUnicast ")
            return
        }
        if (video.createdBy == null) {
            logger.warn("video:${video.stringId} has not createdBy field")
            return
        }
        val extraField = EntityStatusChangePushData(
            video.stringId,
            PushEntityType.VIDEO,
            status.toString()
        )
        val title = when (status) {
            DocumentSubStatus.PUBLISHED_CHECK_PASS -> "您发布的视频审核已通过"
            DocumentSubStatus.DRAFT_ADMIN_REFUSE -> "您发布的视频审核未通过"
            DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL -> "您发布的视频转码失败"
            else -> "您发布的视频状态发生改变"
        }

        val body = when (status) {
            DocumentSubStatus.PUBLISHED_CHECK_PASS -> "视频${video.name}审核已通过，赶紧点击查看"
            DocumentSubStatus.DRAFT_ADMIN_REFUSE -> "视频${video.name}审核未通过，点击查看原因"
            DocumentSubStatus.DRAFT_VIDEO_TRANS_FAIL -> "视频${video.name}转码失败，请重新编辑或删除"
            else -> "您发布的视频状态发生改变,请点击查看"
        }
        val customcast = Customcast(
            title,
            body,
            PushExtraField(
                PushType.ENTITY_STATUS_CHANGE,
                extraField
            ),
            listOf("client"),
            listOf(video.createdBy!!.stringId)
        )
        sendAndroidCustomcast(customcast)
        sendIOSCustomcast(customcast)
    }

    override fun constructEntityPushExtra(type: PushEntityType, id: UUID?): PushExtraField {
        return when (type) {
            PushEntityType.RELEASE -> {
                val img = SpringContext
                    .getBean("releaseRepository", ReleaseRepository::class.java)
                    .findById(id!!)
                    .let {
                        if (it.isPresent) {
                            it.get().images.firstOrNull()
                        } else {
                            null
                        }
                    }

                PushExtraField(
                    PushType.ENTITY,
                    EntityPushData(
                        id = id.toString(),
                        type = type,
                        url = img
                    )
                )
            }
            PushEntityType.ARTIST -> {
                val img = SpringContext
                    .getBean("artistRepository", ArtistRepository::class.java)
                    .findById(id!!)
                    .let {
                        if (it.isPresent) {
                            it.get().images.firstOrNull()
                        } else {
                            null
                        }
                    }
                PushExtraField(
                    PushType.ENTITY,
                    EntityPushData(
                        id = id.toString(),
                        type = type,
                        url = img
                    )
                )
            }
            PushEntityType.WORK -> {
                PushExtraField(
                    PushType.ENTITY,
                    EntityPushData(
                        id = id.toString(),
                        type = type
                    )
                )
            }
            PushEntityType.VIDEO -> {
                val url = SpringContext
                    .getBean("videoRepository", VideoRepository::class.java)
                    .findById(id!!)
                    .let {
                        if (it.isPresent) {
                            it.get().images.firstOrNull()
                        } else {
                            null
                        }
                    }
                PushExtraField(
                    PushType.ENTITY,
                    EntityPushData(
                        id = id.toString(),
                        type = type,
                        url = url
                    )
                )
            }
            PushEntityType.ARTICLE -> {
                val url = SpringContext
                    .getBean("literatureRepository", LiteratureRepository::class.java)
                    .findById(id!!)
                    .let {
                        if (it.isPresent) {
                            it.get().relatedSite
                        } else {
                            null
                        }
                    }
                PushExtraField(
                    PushType.ENTITY,
                    EntityPushData(
                        id = id.toString(),
                        type = type,
                        url = url
                    )
                )
            }
            PushEntityType.BANNER -> {
                val url = SpringContext
                    .getBean("bannerRepository", BannerRepository::class.java)
                    .findById(id!!)
                    .let {
                        if (it.isPresent) {
                            it.get().relatedSite
                        } else {
                            null
                        }
                    }
                PushExtraField(
                    PushType.ENTITY,
                    EntityPushData(
                        id = id.toString(),
                        type = type,
                        url = url
                    )
                )
            }
            PushEntityType.TEXT -> {
                PushExtraField(
                    PushType.MSG,
                    MsgPushData()
                )
            }
        }
    }

    fun <T> sendAndroidCustomcast(unicast: Customcast<T>) {
        val cast = umengService.androidCustomizedcast()
        cast.setTitle(unicast.title)
        cast.setText(unicast.content)
        cast.setTicker(unicast.ticker)
        cast.setAlias(unicast.aliaes.joinToString(","), unicast.aliasType.joinToString(","))
        cast.setDisplayType(AndroidNotification.DisplayType.NOTIFICATION)
        cast.setExtra(unicast.extra)
        cast.setMiPush(true)
        cast.setMiActivity("com.boostfield.musicbible.push.PushDispatcherActivity")
        umengService.send(cast)
    }

    fun <T> sendIOSCustomcast(unicast: Customcast<T>) {
        val cast = umengService.iOSCustomizedcast()
        cast.setTitleAndBody(unicast.title, unicast.content)
        cast.setMutableContent(1)
        cast.setBadge(1)
        cast.setSound("default")
        cast.setAlias(unicast.aliaes.joinToString(","), unicast.aliasType.joinToString(","))
        cast.setExtra(unicast.extra)
        umengService.send(cast)
    }

    fun <T> sendAndroidBroadcast(broadcast: Broadcast<T>) {
        val androidBroadcast = umengService.androidBroacast()
        androidBroadcast.setTitle(broadcast.title)
        androidBroadcast.setDisplayType(AndroidNotification.DisplayType.NOTIFICATION)
        androidBroadcast.setTicker(broadcast.ticker)
        androidBroadcast.setText(broadcast.content)
        androidBroadcast.setExtra(broadcast.extra)
        androidBroadcast.setMiPush(true)
        androidBroadcast.setMiActivity("com.boostfield.musicbible.push.PushDispatcherActivity")
        umengService.send(androidBroadcast)
    }

    fun <T> sendIOSBroadcast(broadcast: Broadcast<T>) {
        val iosBroadcast = umengService.iOSBroadcast()
        iosBroadcast.setTitleAndBody(broadcast.title, broadcast.content)
        iosBroadcast.setMutableContent(1)
        iosBroadcast.setExtra(broadcast.extra)
        iosBroadcast.setBadge(1)
        iosBroadcast.setSound("default")
        umengService.send(iosBroadcast)
    }
}

/**
 * 通过alias推送
 */
data class Customcast<T>(
    val title: String,
    val content: String,
    val extra: T,
    val aliasType: List<String>,
    val aliaes: List<String>,
    val ticker: String = "音乐圣经"
)

/**
 * 广播推送
 */
data class Broadcast<T>(
    val title: String,
    val content: String,
    val deviceTypes: List<DeviceType>,
    var extra: T,
    val ticker: String = "音乐圣经"
)

/************
 *
 *  具体放到推送extra字段里的结构定义
 *
 ***********/

/**
 * extra field
 */
data class PushExtraField(
    val type: PushType,
    val data: PushData
) {
    fun toJson(): String =
        Gson().toJson(this)
}

/**
 * 推送类型
 */
enum class PushType {
    ENTITY, //实体
    URL,
    MSG, //简单消息推送，点击进入应用主页
    ENTITY_STATUS_CHANGE, //实体状态变更
    SYSTEM, //系统通知
}


abstract class PushData(
    var timestamp: Long
)

/***
 * 推送一个实体
 */
data class EntityPushData(
    val id: String,
    val type: PushEntityType,
    val url: String? = null,
    val ts: Long = ZonedDateTime.now().toInstant().toEpochMilli()
) : PushData(ts)

/**
 * 推送一个url
 */

data class UrlPushData(
    val address: String,
    val ts: Long = ZonedDateTime.now().toInstant().toEpochMilli()
) : PushData(ts)


/**
 * 推送msg
 */

data class MsgPushData(
    val ts: Long = ZonedDateTime.now().toInstant().toEpochMilli()
) : PushData(ts)

/**
 * 实体状态变更
 */
data class EntityStatusChangePushData(
    val id: String,
    val type: PushEntityType,
    var status: String,
    val ts: Long = ZonedDateTime.now().toInstant().toEpochMilli()
) : PushData(ts)

/**
 * 系统推送
 */
data class SystemPushData(
    val ts: Long = ZonedDateTime.now().toInstant().toEpochMilli()
) : PushData(ts)
