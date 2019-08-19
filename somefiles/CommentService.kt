package com.musicbible.service

import com.boostfield.aliyun.green.AliyunGreenService
import com.boostfield.aliyun.green.ScanTextResult
import com.boostfield.aliyun.green.SpamTextLabel
import com.boostfield.aliyun.green.SuggestionEnum
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.boostfield.spring.service.SoftDeletableService
import com.musicbible.aspect.Locked
import com.musicbible.event.CommentNotificationEvent
import com.musicbible.model.Appreciation
import com.musicbible.model.Artist
import com.musicbible.model.CanComment
import com.musicbible.model.Comment
import com.musicbible.model.ModelEnum
import com.musicbible.model.Recording
import com.musicbible.model.Release
import com.musicbible.model.Sale
import com.musicbible.model.User
import com.musicbible.model.Video
import com.musicbible.model.Work
import com.musicbible.repository.CommentRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.IllegalStateException
import java.util.UUID

interface CommentService : CommentRepository, SoftDeletableService<Comment> {
    override val modelName: String
        get() = "评论"

    fun findByTarget(targetId: UUID, pageable: Pageable) =
        findByDeletedFalseAndTargetId(targetId, pageable)

    fun findByCreator(user: User, pageable: Pageable) =
        findByDeletedFalseAndCreatedBy(user, pageable)

    fun create(targetType: ModelEnum, targetId: UUID, content: String, replyToId: UUID?): Comment

    fun createAndCheck(targetType: ModelEnum, targetId: UUID, content: String, replyToId: UUID?, user: User): ScanTextResult

    fun list(targetId: UUID, pageable: Pageable): Page<Comment>

    fun listByCreator(creator: User, pageable: Pageable): Page<Comment>

    fun findTarget(comments: Iterable<Comment>): List<Any>

    fun replies(id: UUID, pageable: Pageable): Page<Comment>

    fun refreshReplyCount(comment: Comment)

    fun updateCommentCount(targetType: ModelEnum, targetId: UUID)
}

@Service
@Transactional
class CommentServiceImpl(
    @Autowired val commentRepository: CommentRepository,
    @Autowired val releaseService: ReleaseService,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired val appreciationService: AppreciationService,
    @Autowired val saleService: SaleService,
    @Autowired val videoService: VideoService,
    @Autowired val workService: WorkService,
    @Autowired val artistService: ArtistService,
    @Autowired val aliyunGreenService: AliyunGreenService,
    @Autowired val applicationEventPublisher: ApplicationEventPublisher
) : CommentService, CommentRepository by commentRepository {

    override fun create(targetType: ModelEnum, targetId: UUID, content: String, replyToId: UUID?): Comment {
        if (!targetType.canComment)
            throw AppError.BadRequest.paramError("无法为 ${targetType.cnName} 添加评论")
        val replyTo = replyToId?.let(::findExistsOrThrow)

        val target = targetRepositoryProvider
            .get(targetType)
            .findByDeletedFalseAndId(targetId)
            ?: throw AppError.NotFound.default(msg = "无法找到评论对象: $targetId")

        var comment = Comment().also {
            it.replyTo = replyTo
            it.targetType = targetType
            it.targetId = targetId
            it.content = content
        }

        comment = save(comment)
        replyTo?.also { refreshReplyCount(it) }
        updateCommentCount(targetType, targetId)
        targetRepositoryProvider.get(targetType).save(target)
        applicationEventPublisher.publishEvent(CommentNotificationEvent(comment, targetType, targetId))
        return comment
    }

    @Locked("%{#user.id}-%{#targetId}")
    override fun createAndCheck(targetType: ModelEnum, targetId: UUID, content: String, replyToId: UUID?, user: User): ScanTextResult {
        if (!targetType.canComment)
            throw AppError.BadRequest.paramError("无法为 ${targetType.cnName} 添加评论")

        return aliyunGreenService.scanText(content, user.stringId).also {
            if (it.suggestion == SuggestionEnum.pass) {
                val replyTo = replyToId?.let(::findExistsOrThrow)

                val target = targetRepositoryProvider
                    .get(targetType)
                    .findByDeletedFalseAndId(targetId)
                    ?: throw AppError.NotFound.default(msg = "无法找到评论对象: $targetId")

                var comment = Comment().also { comment ->
                    comment.replyTo = replyTo
                    comment.targetType = targetType
                    comment.targetId = targetId
                    comment.content = content
                }

                comment = save(comment)
                replyTo?.also { refreshReplyCount(it) }
                updateCommentCount(targetType, targetId)
                targetRepositoryProvider.get(targetType).save(target)
                applicationEventPublisher.publishEvent(CommentNotificationEvent(comment, targetType, targetId))
            } else {
                throw AppError.Forbidden.default(msg = "您所上传的内容涉嫌含有${it.label.getFeedbackText()}内容,请删除后重新发布")
            }
        }
    }

    override fun replies(id: UUID, pageable: Pageable): Page<Comment> {
        val parent = findExistsOrThrow(id)
        return findByDeletedFalseAndReplyTo(parent, pageable)
    }

    override fun list(targetId: UUID, pageable: Pageable): Page<Comment> {
        return findByTarget(targetId, pageable)
    }

    override fun listByCreator(creator: User, pageable: Pageable): Page<Comment> {
        return findByCreator(creator, pageable)
    }

    override fun findTarget(comments: Iterable<Comment>): List<Any> {
        // Optimize: 按照类型分组后再查询，可以减少查询次数
        return comments.map {
            targetRepositoryProvider
                .get(it.targetType)
                .findById(it.targetId)
                .orElseThrow { AppError.NotFound.default(msg = "需要的数据已从数据库彻底删除,请联系管理员处理") }
        }
    }

    override fun refreshReplyCount(comment: Comment) {
        val count = countByDeletedFalseAndReplyTo(comment)
        comment.replyCount = count
        save(comment)
    }

    override fun updateCommentCount(targetType: ModelEnum, targetId: UUID) {
        val target = targetRepositoryProvider
            .get(targetType)
            .findByDeletedFalseAndId(targetId)

        (target as CanComment).commentCount = countByDeletedFalseAndTargetId(targetId)
        targetRepositoryProvider.get(targetType).save(target)
        when (target) {
            is Video -> videoService.indexToEs(target)
            is Appreciation -> appreciationService.indexToEs(target)
            is Sale -> saleService.indexToEs(target)
            is Release -> releaseService.indexToEs(target)
            is Work -> workService.indexToEs(target)
            is Artist -> artistService.indexToEs(target)
            is Recording -> Unit
            else -> throw IllegalStateException("不支持为 $targetType 评论")
        }
    }

    fun SpamTextLabel.getFeedbackText(): String {
        return when (this) {
            SpamTextLabel.normal -> "正常文本"
            SpamTextLabel.spam -> "垃圾内容"
            SpamTextLabel.abuse -> "辱骂"
            SpamTextLabel.ad -> "广告"
            SpamTextLabel.politics -> "涉政"
            SpamTextLabel.terrorism -> "暴恐"
            SpamTextLabel.porn -> "色情"
            SpamTextLabel.flood -> "灌水"
            SpamTextLabel.contraband -> "违禁"
            SpamTextLabel.meaningless -> "无意义"
            SpamTextLabel.customized -> "自定义"
        }
    }
}
