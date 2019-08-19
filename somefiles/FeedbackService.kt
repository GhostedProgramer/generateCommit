package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.BaseService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.feedback.FeedbackListInput
import com.musicbible.model.Feedback
import com.musicbible.model.QFeedback
import com.musicbible.model.User
import com.musicbible.repository.FeedbackRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface FeedbackService : FeedbackRepository, BaseService<Feedback> {
    override val modelName: String
        get() = "反馈"

    fun manageFeedBackList(feedbackInput: FeedbackListInput): Page<Feedback>
    fun solve(user: User, id: UUID)
}

@Service
@Transactional
class FeedbackServiceImpl(
    @Autowired val feedbackRepository: FeedbackRepository,
    @Autowired @PersistenceContext val em: EntityManager
) : FeedbackService, FeedbackRepository by feedbackRepository {

    val qFeedBack: QFeedback = QFeedback.feedback

    @Locked("%{#user.id}-%{#id}")
    override fun solve(user: User, id: UUID) {
        val feedback = findByDeletedFalseAndId(id)
        if (feedback == null) {
            throw AppError.NotFound.default(msg = "找不到id为$id 的反馈,可能已被删除")
        } else {
            feedback.solver = user
            feedback.isSolved = true
            save(feedback)
        }
    }

    override fun manageFeedBackList(feedbackInput: FeedbackListInput): Page<Feedback> {
        var predicate = qFeedBack.deleted.isFalse
        feedbackInput.type?.also {
            predicate = predicate.and(qFeedBack.type.eq(feedbackInput.type))
        }
        feedbackInput.deviceType?.also {
            predicate = predicate.and(qFeedBack.deviceType.eq(feedbackInput.deviceType))
        }
        feedbackInput.isSolved?.also {
            predicate = predicate.and(qFeedBack.isSolved.eq(feedbackInput.isSolved))
        }
        if (feedbackInput.content.isNotEmpty()) {
            predicate = predicate.and(qFeedBack.content.like("%${feedbackInput.content}%"))
        }
        return findAll(predicate, feedbackInput.defaultSort("+isSolved", "-updatedAt"))
    }
}
