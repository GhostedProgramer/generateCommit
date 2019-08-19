package com.musicbible.service

import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.exception.NotificationException
import com.musicbible.factory.notification.NotificationBuilder
import com.musicbible.model.Notification
import com.musicbible.model.NotificationType
import com.musicbible.model.QNotification
import com.musicbible.model.User
import com.musicbible.repository.NotificationRepository
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAUpdateClause
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface NotificationService : NotificationRepository, TimeKeepingService<Notification> {
    override val modelName: String
        get() = "通知"

    fun page(
        notificationType: NotificationType? = null,
        title: String? = null,
        read: Boolean? = null,
        receiverId: UUID? = null,
        pageable: Pageable
    ): Page<Notification>

    //创建系统通知
    fun createSystem(title: String, content: String, createdBy: User? = null): Notification

    fun createSystem(notificationBuilder: NotificationBuilder): Notification

    //批量设置已读
    fun setRead(ids: MutableList<UUID>, notificationType: NotificationType? = null, userId: UUID)

    fun count(receiver: User, notificationType: NotificationType? = null): Long

    fun countGroupNotificationType(receiver: User): MutableMap<NotificationType, Long>

    fun backendList(title: String?, pageable: Pageable): Page<Notification>

    fun frontendList(notificationType: NotificationType?, user: User, pageable: Pageable): Page<Notification>
}

@Service
@Transactional
class NotificationServiceImpl(
    @Autowired val notificationRepository: NotificationRepository,
    @Autowired val userService: UserService,
    @Autowired @PersistenceContext val em: EntityManager
) : NotificationService, NotificationRepository by notificationRepository {

    val qNotification: QNotification = QNotification.notification

    override fun frontendList(notificationType: NotificationType?, user: User, pageable: Pageable): Page<Notification> {
        if (notificationType == null) {
            throw NotificationException.typeUndefined()
        }
        var criteria = qNotification.notificationType.eq(notificationType)
        criteria = if (notificationType == NotificationType.SYSTEM) {
            criteria.and(qNotification.receiver.isNull.or(qNotification.receiver.eq(user)))
        } else {
            criteria.and(qNotification.receiver.eq(user))
        }
        return findAll(criteria, pageable)
    }

    override fun backendList(title: String?, pageable: Pageable): Page<Notification> {
        var criteria = qNotification.notificationType.eq(NotificationType.SYSTEM).and(qNotification.receiver.isNull)
        title?.also {
            criteria = criteria.and(qNotification.title.contains(title))
        }
        return findAll(criteria, pageable)
    }

    override fun page(
        notificationType: NotificationType?,
        title: String?,
        read: Boolean?,
        receiverId: UUID?,
        pageable: Pageable
    ): Page<Notification> {
        val system = userService.findByUserName("SYSTEM")
        var criteria = if (notificationType == null) {
            qNotification.notificationType.isNotNull
        } else {
            qNotification.notificationType.eq(notificationType)
        }
        // 如果没有接收者且通知类型为SYSTEM则获取后台系统通知
        if (receiverId == null && notificationType == NotificationType.SYSTEM) {
            val query = JPAQuery<Notification>(em)
                .select(
                    qNotification.subject, qNotification.subjectLocation,
                    qNotification.title, qNotification.titlePlaceholder,
                    qNotification.content, qNotification.contentPlaceholder,
                    qNotification.notificationType, qNotification.createdAt
                )
                .from(qNotification)
                .where(criteria.and(qNotification.createdBy.ne(system)))
                .groupBy(
                    qNotification.subject, qNotification.subjectLocation,
                    qNotification.title, qNotification.titlePlaceholder,
                    qNotification.content, qNotification.contentPlaceholder,
                    qNotification.notificationType, qNotification.createdAt
                )
            val result = query.fetch().map { tuple ->
                val builder = NotificationBuilder()
                tuple[qNotification.subjectLocation]?.also { builder.subjectLocation(it) }
                tuple[qNotification.title]?.also { title ->
                    tuple[qNotification.content]?.also { content -> builder.system(title, content) }
                }
                tuple[qNotification.notificationType]?.also { builder.type(it) }
                val notification = builder.buildToBackendOutput()
                tuple[qNotification.subject]?.also { notification.subject = it }
                tuple[qNotification.titlePlaceholder]?.also { notification.titlePlaceholder = it }
                tuple[qNotification.contentPlaceholder]?.also { notification.contentPlaceholder = it }
                tuple[qNotification.createdAt]?.also { notification.createdAt = it }
                notification
            }
            val count = countByGroup()
            return PageImpl(result, pageable, count)
        } else if (receiverId != null) {
            criteria = criteria.and(qNotification.receiver.id.eq(receiverId))
            title?.also { criteria = criteria.and(qNotification.title.like("%$title%")) }
            return findAll(criteria, pageable)
        } else {
            throw NotificationException("当前没有用户登陆，不允许获取系统通知以外的通知")
        }
    }

    override fun createSystem(title: String, content: String, createdBy: User?): Notification {
        val notification = NotificationBuilder()
            .system(title, content)
            .type(NotificationType.SYSTEM)
            .build()
            .also {
                it.createdAt = ZonedDateTime.now()
                it.createdBy = createdBy
                it.receiver = null
            }
        return save(notification)
    }

    override fun createSystem(notificationBuilder: NotificationBuilder): Notification {
        val notification =
            notificationBuilder
                .build()
                .also {
                    it.receiver = null
                }
        return save(notification)
    }

    @Locked("%{#userId}")
    override fun setRead(ids: MutableList<UUID>, notificationType: NotificationType?, userId: UUID) {
        val qNotification = QNotification.notification
        val updateClause = JPAUpdateClause(em, qNotification)
        if (notificationType != null) {
            updateClause.where(qNotification.notificationType.eq(notificationType))
        }
        if (ids.size > 0) {
            updateClause.where(qNotification.id.`in`(ids))
        } else {
            updateClause.where(qNotification.receiver.id.eq(userId))
        }
        updateClause.set(qNotification.read, true)
            .execute()
    }

    override fun count(receiver: User, notificationType: NotificationType?): Long {
        val qNotification = QNotification.notification
        var criteria = qNotification.receiver.id.eq(receiver.id)
            .and(qNotification.read.isFalse)
        notificationType?.also { criteria = criteria.and(qNotification.notificationType.eq(notificationType)) }
        return count(criteria)
    }

    override fun countGroupNotificationType(receiver: User): MutableMap<NotificationType, Long> {
        val countMap = mutableMapOf<NotificationType, Long>()
        NotificationType.values().forEach {
            countMap[it] = count(receiver, it)
        }
        return countMap
    }
}
