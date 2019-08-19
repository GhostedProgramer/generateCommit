package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.event.SendMiniAppRecommendationEvent
import com.musicbible.mapper.recommendation.CreateRecommendationInput
import com.musicbible.mapper.recommendation.RecommendationFrontendListInput
import com.musicbible.mapper.recommendation.RecommendationListInput
import com.musicbible.model.QRecommendation
import com.musicbible.model.Recommendation
import com.musicbible.model.User
import com.musicbible.repository.RecommendationRepository
import com.querydsl.core.BooleanBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

interface RecommendationService :
    TimeKeepingService<Recommendation>, RecommendationRepository {
    override val modelName: String
        get() = "小程序每日推荐"

    fun findList(input: RecommendationListInput): Page<Recommendation>
    fun findFrontendList(input: RecommendationFrontendListInput): Page<Recommendation>
    fun delete(id: UUID)
    fun create(user: User, input: CreateRecommendationInput): Recommendation
    fun remove(id: UUID)
    fun findLatestAndCheck(): Recommendation
    fun findRight(id: UUID): Recommendation?
    fun findLeft(id: UUID): Recommendation?
}

@Service
@Transactional
class RecommendationServiceImp(
    @Autowired val recommendationRepository: RecommendationRepository,
    @Autowired val releaseService: ReleaseService,
    @Autowired val quartzService: QuartzService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : RecommendationService, RecommendationRepository by recommendationRepository {

    val qRecommendation: QRecommendation = QRecommendation.recommendation

    @Locked("%{#id}")
    override fun delete(id: UUID) {
        findAndDelete(id)
    }

    override fun findList(input: RecommendationListInput): Page<Recommendation> {
        var predicate = BooleanBuilder()
        input.q?.also {
            predicate = predicate.and(qRecommendation.release.titleCN.contains(input.q))
        }
        return findAll(predicate, input.defaultSortByCreateAt())
    }

    override fun findFrontendList(input: RecommendationFrontendListInput): Page<Recommendation> {
        return findAll(qRecommendation.isSend.isTrue, input.defaultSortByCreateAt())
    }

    override fun create(user: User, input: CreateRecommendationInput): Recommendation {
        val recommendation = Recommendation().apply {
            release = releaseService.findExistsOrThrow(input.releaseId)
            createdBy = user
            if (input.expectSendTime == null) {
                isSend = true
                expectSendTime = ZonedDateTime.now()
            } else {
                expectSendTime = quartzService.transFromStringToZonedDateTime(input.expectSendTime!!)
            }
        }
        val newRecommendation = save(recommendation)
        /*发送任务定时,定时修改isSend字段,如创建recommendation时未设置定时发送时间,则不添加定时任务*/
        eventPublisher.publishEvent(SendMiniAppRecommendationEvent(newRecommendation, input.expectSendTime))
        return newRecommendation
    }

    @Locked("%{#id}")
    override fun remove(id: UUID) {
        val recommendation = findOrThrow(id)
        if (recommendation.expectSendTime!! < ZonedDateTime.now()) {
            throw AppError.BadRequest.default(msg = "推荐已发送,不可删除")
        } else {
            delete(id)
            quartzService.removeJob(id.toString())
        }
    }

    override fun findLatestAndCheck(): Recommendation {
        return recommendationRepository.findLatest()
            ?: throw AppError.NotFound.default(msg = "暂无推荐")
    }

    override fun findRight(id: UUID): Recommendation? {
        val list = findAll().filter { it.isSend }.sortedByDescending { it.expectSendTime }
        if (list.isEmpty()) {
            throw AppError.NotFound.default(msg = "暂无推荐")
        }
        val origin = findOrThrow(id)
        val index = list.indexOf(origin)
        return if (index + 1 == list.size) {
            null
        } else {
            list[index + 1]
        }
    }

    override fun findLeft(id: UUID): Recommendation? {
        val list = findAll().filter { it.isSend }.sortedByDescending { it.expectSendTime }
        if (list.isEmpty()) {
            throw AppError.NotFound.default(msg = "暂无推荐")
        }
        val origin = findOrThrow(id)
        val index = list.indexOf(origin)
        return if (index - 1 == -1) {
            null
        } else {
            list[index - 1]
        }
    }
}
