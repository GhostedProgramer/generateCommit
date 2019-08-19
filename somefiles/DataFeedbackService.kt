package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.event.DataFeedbackNotificationEventToPublisher
import com.musicbible.event.DataFeedbackNotificationEventToSubmitter
import com.musicbible.mapper.dataFeedback.DataFeedbackBackendListInput
import com.musicbible.mapper.dataFeedback.DataFeedbackCreateInput
import com.musicbible.model.Artist
import com.musicbible.model.DataFeedback
import com.musicbible.model.DataFeedbackStatus
import com.musicbible.model.Document
import com.musicbible.model.Label
import com.musicbible.model.QDataFeedback
import com.musicbible.model.Release
import com.musicbible.model.User
import com.musicbible.model.Work
import com.musicbible.repository.DataFeedbackRepository
import com.musicbible.repository.base.DocumentRepository
import com.querydsl.core.BooleanBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface DataFeedbackService : TimeKeepingService<DataFeedback>, DataFeedbackRepository {
    override val modelName: String
        get() = "数据报错"

    fun getBackendList(input: DataFeedbackBackendListInput): Page<DataFeedback>
    fun ignore(dataFeedback: DataFeedback)
    fun modify(dataFeedback: DataFeedback)
    fun create(input: DataFeedbackCreateInput, user: User): DataFeedback
}

@Service
@Transactional
class DataFeedbackServiceImpl(
    @Autowired val dataFeedbackRepository: DataFeedbackRepository,
    @Autowired val targetRepositoryProvider: RepositoryProvider<DocumentRepository<Document>>,
    @Autowired val applicationEventPublisher: ApplicationEventPublisher
) : DataFeedbackService, DataFeedbackRepository by dataFeedbackRepository {

    val qDataFeedback: QDataFeedback = QDataFeedback.dataFeedback

    override fun getBackendList(input: DataFeedbackBackendListInput): Page<DataFeedback> {
        var predicate = BooleanBuilder()
        input.targetType?.also {
            predicate = predicate.and(qDataFeedback.targetType.eq(input.targetType))
        }
        input.status?.also {
            predicate = predicate.and(qDataFeedback.status.eq(input.status))
        }
        if (input.q.isNotEmpty()) {
            predicate = predicate.and(qDataFeedback.description.contains(input.q))
        }
        return findAll(predicate, input.defaultSort("-status", "-updatedAt"))
    }

    @Locked("%{#dataFeedback.id}")
    override fun ignore(dataFeedback: DataFeedback) {
        dataFeedback.status = DataFeedbackStatus.Ignored
        save(dataFeedback)
    }

    @Locked("%{#dataFeedback.id}")
    override fun modify(dataFeedback: DataFeedback) {
        dataFeedback.status = DataFeedbackStatus.Modified
        val newDataFeedback = save(dataFeedback)
        val target = targetRepositoryProvider
            .get(newDataFeedback.targetType)
            .findByDeletedFalseAndPublishedTrueAndId(newDataFeedback.targetId)
        if (target != null) {
            applicationEventPublisher.publishEvent(
                DataFeedbackNotificationEventToPublisher(
                    newDataFeedback,
                    newDataFeedback.targetType,
                    newDataFeedback.targetId,
                    target
                )
            )
            applicationEventPublisher.publishEvent(
                DataFeedbackNotificationEventToSubmitter(
                    newDataFeedback,
                    newDataFeedback.targetType,
                    newDataFeedback.targetId,
                    target
                )
            )
        } else {
            throw AppError.NotFound.default(msg = "该${newDataFeedback.targetType.cnName}已被删除")
        }
    }

    @Locked("%{#user.id}-%{#input.targetId}")
    override fun create(input: DataFeedbackCreateInput, user: User): DataFeedback {
        val target = targetRepositoryProvider.get(input.targetType)
            .findByDeletedFalseAndPublishedTrueAndId(input.targetId)
        if (target!!.createdBy == user) {
            throw AppError.BadRequest.default(msg = "请勿报错自己发布的资源")
        }
        return DataFeedback().let {
            it.content = input.content
            it.images = input.images
            it.targetId = input.targetId
            it.targetType = input.targetType
            it.createdBy = user
            it.status = DataFeedbackStatus.Unhandled
            it.description = when (target) {
                is Release -> "${target.titleCN},${target.title1},${target.catalogs}"
                is Artist -> "${target.nameCN},${target.firstName},${target.lastName}"
                is Work -> "${target.titleCN},${target.title}"
                is Label -> "${target.name},${target.nameCN}"
                else -> throw AppError.NotFound.default(msg = "该类型实体不可报错")
            }
            save(it)
        }
    }
}
