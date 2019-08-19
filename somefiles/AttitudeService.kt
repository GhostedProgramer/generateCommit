package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.musicbible.aspect.Locked
import com.musicbible.event.AttitudeNotificationEvent
import com.musicbible.event.EntityEvent
import com.musicbible.event.virtualdata.VirtualAttitudeDataEvent
import com.musicbible.listener.virtualdata.VirtualDataEventListener
import com.musicbible.model.Appreciation
import com.musicbible.model.Attitude
import com.musicbible.model.AttitudeAction
import com.musicbible.model.CanAttitude
import com.musicbible.model.ModelEnum
import com.musicbible.model.User
import com.musicbible.model.Video
import com.musicbible.repository.AttitudeRepository
import com.musicbible.service.vest.TempVestService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface AttitudeService : AttitudeRepository {
    fun lastAttitude(user: User, targetId: UUID): Attitude?

    //用户目前的态度
    fun lastAttitudeAction(user: User, targetId: UUID) =
        lastAttitude(user, targetId)?.action

    // 最后点赞的若干条记录
    fun lastLikes(targetId: UUID, count: Int): List<Attitude>

    // 最终点赞结果
    fun finalAttitudeAction(user: User, targetId: UUID): AttitudeAction?

    // 最终是否喜欢
    fun isLikeFinally(user: User, targetId: UUID) =
        finalAttitudeAction(user, targetId) == AttitudeAction.LIKE

    // 最终是否不喜欢
    fun isDislikeFinally(user: User, targetId: UUID) =
        finalAttitudeAction(user, targetId) == AttitudeAction.DISLIKE

    fun like(user: User, targetType: ModelEnum, targetId: UUID): Attitude?
    fun cancelLike(user: User, targetType: ModelEnum, targetId: UUID)
    fun dislike(user: User, targetType: ModelEnum, targetId: UUID): Attitude?
    fun cancelDisLike(user: User, targetType: ModelEnum, targetId: UUID)
    fun likeForTempVest(user: User, targetType: ModelEnum, targetId: UUID)
}

@Service
class AttitudeServiceImpl(
    @Autowired val attitudeRepository: AttitudeRepository,
    @Autowired val videoService: VideoService,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired val appreciationService: AppreciationService,
    @Autowired val tempVestService: TempVestService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : AttitudeService, AttitudeRepository by attitudeRepository {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    private fun create(user: User, targetType: ModelEnum, targetId: UUID, action: AttitudeAction): Attitude {
        val attitude = Attitude().also {
            it.createdBy = user
            it.targetType = targetType
            it.targetId = targetId
            it.action = action
        }
        return save(attitude)
    }

    override fun lastAttitude(user: User, targetId: UUID): Attitude? {
        return findFirstByTargetIdAndCreatedByOrderByUpdatedAtDesc(targetId, user).firstOrNull()
    }

    override fun finalAttitudeAction(user: User, targetId: UUID): AttitudeAction? {
        return lastAttitudeAction(user, targetId)?.let {
            when (it) {
                AttitudeAction.LIKE, AttitudeAction.DISLIKE -> it
            }
        }
    }

    override fun lastLikes(targetId: UUID, count: Int): List<Attitude> {
        val list = mutableListOf<Attitude>()
        findByTargetIdAndActionOrderByUpdatedAtDesc(targetId, AttitudeAction.LIKE, PageRequest.of(0, count))
            .forEach {
                list.add(it)
            }
        return list
    }

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun like(user: User, targetType: ModelEnum, targetId: UUID): Attitude? {
        validateCanAttitude(targetType, targetId)
        return when (lastAttitudeAction(user, targetId)) {
            AttitudeAction.LIKE -> null
            null -> create(user, targetType, targetId, AttitudeAction.LIKE)
                .also {
                    updateLikeCount(targetType, targetId)
                    publishAttitudeNotification(targetType, targetId, it, user)
                    eventPublisher.publishEvent(
                        VirtualAttitudeDataEvent(
                            targetId, targetType, tempVestService.getVestUserSet(VirtualDataEventListener.REPEAT_COUNT_FOR_ATTITUDE)
                        )
                    )
                }
            AttitudeAction.DISLIKE -> {
                delete(lastAttitude(user, targetId)!!)
                create(user, targetType, targetId, AttitudeAction.LIKE)
                    .also {
                        updateLikeCount(targetType, targetId)
                        updateDislikeCount(targetType, targetId)
                        publishAttitudeNotification(targetType, targetId, it, user)
                    }
            }
        }
    }

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun likeForTempVest(user: User, targetType: ModelEnum, targetId: UUID) {
        when (lastAttitudeAction(user, targetId)) {
            AttitudeAction.LIKE -> Unit
            null -> {
                val attitude = Attitude().also {
                    it.createdBy = user
                    it.targetType = targetType
                    it.targetId = targetId
                    it.action = AttitudeAction.LIKE
                }
                save(attitude)
                updateLikeCount(targetType, targetId)
                publishAttitudeNotification(targetType, targetId, attitude, user)
            }
            AttitudeAction.DISLIKE -> {
                delete(lastAttitude(user, targetId)!!)
                val attitude = Attitude().also {
                    it.createdBy = user
                    it.targetType = targetType
                    it.targetId = targetId
                    it.action = AttitudeAction.LIKE
                }
                save(attitude)
                updateLikeCount(targetType, targetId)
                updateDislikeCount(targetType, targetId)
                publishAttitudeNotification(targetType, targetId, attitude, user)
            }
        }
    }

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun cancelLike(user: User, targetType: ModelEnum, targetId: UUID) {
        validateCanAttitude(targetType, targetId)

        return when (val currentStatus = lastAttitudeAction(user, targetId)) {
            AttitudeAction.LIKE -> {
                delete(lastAttitude(user, targetId)!!)
                updateLikeCount(targetType, targetId)
            }
            null, AttitudeAction.DISLIKE ->
                throw AppError.BadRequest.illegalOperate("$currentStatus 状态无法取消喜欢")
        }
    }

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun dislike(user: User, targetType: ModelEnum, targetId: UUID): Attitude? {
        validateCanAttitude(targetType, targetId)

        return when (lastAttitudeAction(user, targetId)) {
            AttitudeAction.DISLIKE -> null
            null ->
                create(user, targetType, targetId, AttitudeAction.DISLIKE)
                    .also { updateDislikeCount(targetType, targetId) }
            AttitudeAction.LIKE -> {
                delete(lastAttitude(user, targetId)!!)
                create(user, targetType, targetId, AttitudeAction.DISLIKE)
                    .also {
                        updateLikeCount(targetType, targetId)
                        updateDislikeCount(targetType, targetId)
                    }
            }
        }
    }

    @Locked("%{#user.id}-%{#targetId}")
    @Transactional
    override fun cancelDisLike(user: User, targetType: ModelEnum, targetId: UUID) {
        validateCanAttitude(targetType, targetId)

        when (val currentStatus = lastAttitudeAction(user, targetId)) {
            AttitudeAction.DISLIKE -> {
                delete(lastAttitude(user, targetId)!!)
                updateDislikeCount(targetType, targetId)
            }
            null, AttitudeAction.LIKE ->
                throw AppError.BadRequest.illegalOperate("$currentStatus 状态无法取消不喜欢")
        }
    }

    private fun validateCanAttitude(targetType: ModelEnum, targetId: UUID) {
        if (!targetType.canAttitude)
            throw AppError.BadRequest.paramError("${targetType.cnName}不涉及喜好")

        if (!targetRepositoryProvider.get(targetType).existsByDeletedFalseAndId(targetId))
            throw AppError.NotFound.default(msg = "无法找到喜欢对象: $targetId")
    }

    private fun updateLikeCount(targetType: ModelEnum, targetId: UUID) {
        val target = targetRepositoryProvider
            .get(targetType)
            .findByDeletedFalseAndId(targetId)

        (target as CanAttitude).likeCount = countByTargetIdAndAction(targetId, AttitudeAction.LIKE)
        targetRepositoryProvider.get(targetType).save(target)

        when (target) {
            is Video, is Appreciation -> eventPublisher.publishEvent(EntityEvent.updated(target))
        }
    }

    private fun updateDislikeCount(targetType: ModelEnum, targetId: UUID) {
        val target = targetRepositoryProvider
            .get(targetType)
            .findByDeletedFalseAndId(targetId)

        (target as CanAttitude).dislikeCount = countByTargetIdAndAction(targetId, AttitudeAction.DISLIKE)
        targetRepositoryProvider.get(targetType).save(target)

        when (target) {
            is Video, is Appreciation -> eventPublisher.publishEvent(EntityEvent.updated(target))
        }
    }

    private fun publishAttitudeNotification(targetType: ModelEnum, targetId: UUID, it: Attitude, user: User) {
        when (targetType) {
            ModelEnum.Appreciation -> {
                eventPublisher.publishEvent(AttitudeNotificationEvent(it, targetType, targetId, user))
            }
            ModelEnum.Video -> {
                if (!videoService.findByDeletedFalseAndPublishedTrueAndId(targetId)!!.fromAdmin) {
                    eventPublisher.publishEvent(AttitudeNotificationEvent(it, targetType, targetId, user))
                }
            }
            ModelEnum.Comment -> {
                eventPublisher.publishEvent(AttitudeNotificationEvent(it, targetType, targetId, user))
            }
            else -> logger.debug("点赞对象为${targetType.cnName} ,无需发送通知")
        }
    }
}
