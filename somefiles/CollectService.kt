package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.event.CollectNotificationEvent
import com.musicbible.event.virtualdata.VirtualCollectDataEvent
import com.musicbible.listener.virtualdata.VirtualDataEventListener
import com.musicbible.model.*
import com.musicbible.repository.base.CollectRepository
import com.musicbible.service.vest.TempVestService
import com.querydsl.jpa.JPAExpressions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface CollectService : TimeKeepingService<Collect> {
    override val modelName: String
        get() = "收藏"

    fun pageByCreator(user: User, keyword: String?, targetType: ModelEnum, pageable: Pageable): Page<Collect>

    fun isCollected(user: User?, targetId: UUID): Boolean

    fun collect(user: User, targetId: UUID, targetType: ModelEnum)

    fun cancelCollect(user: User, targetId: UUID, targetType: ModelEnum)

    /**
     * 获取targetId对应实体最近Count条收藏记录
     */
    fun lastCollects(count: Int, targetId: UUID): List<Collect>

    fun collectForTempVest(targetId: UUID, user: User, targetType: ModelEnum)
}

@Service
@Transactional
class CollectServiceImpl(
    @Autowired val collectRepository: CollectRepository,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired @PersistenceContext val em: EntityManager,
    @Autowired val videoService: VideoService,
    @Autowired val workService: WorkService,
    @Autowired val releaseService: ReleaseService,
    @Autowired val artistService: ArtistService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val tempVestService: TempVestService,
    @Autowired val userService: UserService
) : CollectService, CollectRepository by collectRepository {
    val qCollect: QCollect = QCollect.collect
    val qRelease: QRelease = QRelease.release
    val qArtist: QArtist = QArtist.artist
    val qWork: QWork = QWork.work
    val qVideo: QVideo = QVideo.video
    val qRecording: QRecording = QRecording.recording

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun lastCollects(count: Int, targetId: UUID): List<Collect> {
        return findByTargetIdOrderByCreatedAtDesc(targetId, PageRequest.of(0, count)).toMutableList()
    }

    override fun pageByCreator(user: User, keyword: String?, targetType: ModelEnum, pageable: Pageable): Page<Collect> {
        val targetCriteria = when (targetType) {
            ModelEnum.Release -> JPAExpressions.select(qRelease.id).from(qRelease)
                .where(qRelease.deleted.isFalse.let {
                    keyword?.let { kw ->
                        it.and(qRelease.titleCN.contains(kw))
                    } ?: it
                })
            ModelEnum.Artist -> JPAExpressions.select(qArtist.id).from(qArtist)
                .where(qArtist.deleted.isFalse.let {
                    keyword?.let { kw ->
                        it.and(qArtist.nameCN.contains(kw))
                    } ?: it
                })
            ModelEnum.Work -> JPAExpressions.select(qWork.id).from(qWork)
                .where(qWork.deleted.isFalse.let {
                    keyword?.let { kw ->
                        it.and(qWork.titleCN.contains(kw))
                    } ?: it
                })
            ModelEnum.Video -> JPAExpressions.select(qVideo.id).from(qVideo)
                .where(qVideo.deleted.isFalse.let {
                    keyword?.let { kw ->
                        it.and(qVideo.name.contains(kw))
                    } ?: it
                })
            ModelEnum.Recording -> JPAExpressions
                .select(qRecording.id)
                .from(qRecording)
                .where(qRecording.deleted.isFalse.and(
                    keyword?.let { qRecording.title.contains(it).or(qRecording.titleCN.contains(it)) }
                ))
            else -> throw AppError.NotFound.default(msg = "不存在的请求类型: $targetType")
        }
        val originCriteria = qCollect.createdBy.eq(user).and(qCollect.targetType.eq(targetType))
        val finalCriteria = originCriteria.and(qCollect.targetId.`in`(targetCriteria))
        return findAll(finalCriteria, pageable)
    }

    override fun isCollected(user: User?, targetId: UUID): Boolean {
        return user?.let {
            collectRepository.existsByTargetIdAndCreatedBy(targetId, user)
        } ?: false
    }

    @Locked("%{#user.id}-%{#targetId}")
    override fun collect(user: User, targetId: UUID, targetType: ModelEnum) {
        validateCanCollect(targetType, targetId)
        createCollect(targetId, user, targetType)
    }

    @Locked("%{#user.id}-%{#targetId}")
    override fun collectForTempVest(targetId: UUID, user: User, targetType: ModelEnum) {
        if (!collectRepository.existsByTargetIdAndCreatedBy(targetId, user)) { //如果登录用户对需操作实体未进行过收藏操作,则执行之后的操作
            val collect = Collect()
            collect.createdBy = user
            collect.targetId = targetId
            collect.targetType = targetType
            save(collect)
            updateCollectCount(targetType, targetId)
            publishCollectNotification(targetType, targetId, collect, user)
        }
    }

    @Locked("%{#user.id}-%{#targetId}")
    override fun cancelCollect(user: User, targetId: UUID, targetType: ModelEnum) {
        validateCanCollect(targetType, targetId)
        val collect = collectRepository.findByTargetIdAndCreatedBy(targetId, user)
        if (collect != null) {
            collectRepository.delete(collect)
            updateCollectCount(targetType, targetId)
        }
    }

    private fun createCollect(targetId: UUID, user: User, targetType: ModelEnum) {
        if (!collectRepository.existsByTargetIdAndCreatedBy(targetId, user)) { //如果登录用户对需操作实体未进行过收藏操作,则执行之后的操作
            val collect = Collect()
            collect.createdBy = user
            collect.targetId = targetId
            collect.targetType = targetType
            save(collect)
            updateCollectCount(targetType, targetId)
            publishCollectNotification(targetType, targetId, collect, user)
            eventPublisher.publishEvent(
                VirtualCollectDataEvent(
                    targetId, targetType, tempVestService.getVestUserSet(VirtualDataEventListener.REPEAT_COUNT_FOR_COLLECT)
                )
            )
        }
    }

    private fun validateCanCollect(targetType: ModelEnum, targetId: UUID) {
        if (!targetType.canCollect)
            throw AppError.BadRequest.paramError("${targetType.cnName}不涉及收藏")

        if (!targetRepositoryProvider.get(targetType).existsByDeletedFalseAndId(targetId))
            throw AppError.NotFound.default(msg = "无法找到收藏对象: $targetId")
    }

    private fun updateCollectCount(targetType: ModelEnum, targetId: UUID) {
        val target = targetRepositoryProvider
            .get(targetType)
            .findByDeletedFalseAndId(targetId)

        (target as CanCollect).collectCount = countByTargetId(targetId)
        targetRepositoryProvider.get(targetType).save(target)
        when (target) {
            is Video -> videoService.indexToEs(target)
            is Work -> workService.indexToEs(target)
            is Artist -> artistService.indexToEs(target)
            is Release -> releaseService.indexToEs(target)
            is Recording -> Unit
            else -> throw IllegalStateException("无法处理 $targetType 类型的数据")
        }
    }

    private fun publishCollectNotification(targetType: ModelEnum, targetId: UUID, collect: Collect, user: User) {
        when (targetType) {
            ModelEnum.Release -> {
                if (!releaseService.findByDeletedFalseAndPublishedTrueAndId(targetId)!!.fromAdmin) {
                    eventPublisher.publishEvent(CollectNotificationEvent(collect, targetType, targetId, user))
                }
            }
            ModelEnum.Video -> {
                if (!videoService.findByDeletedFalseAndPublishedTrueAndId(targetId)!!.fromAdmin) {
                    eventPublisher.publishEvent(CollectNotificationEvent(collect, targetType, targetId, user))
                }
            }
            ModelEnum.Artist, ModelEnum.Work, ModelEnum.Recording ->
                logger.debug("收藏对象为 $targetType, 无需发送消息通知")
            else -> throw IllegalStateException("无法为 $targetType 类型的数据发送通知")
        }
    }
}
