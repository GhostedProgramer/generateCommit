package com.musicbible.service

import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.event.ReportNotificationEvent
import com.musicbible.mapper.report.ReportBackendListInput
import com.musicbible.mapper.report.ReportFrontendInput
import com.musicbible.model.QReport
import com.musicbible.model.Report
import com.musicbible.model.Status
import com.musicbible.model.User
import com.musicbible.repository.ReportRepository
import com.querydsl.core.BooleanBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


interface ReportService :
    TimeKeepingService<Report>, ReportRepository {
    override val modelName: String
        get() = "举报"

    fun findList(input: ReportBackendListInput): Page<Report>
    fun create(user: User, input: ReportFrontendInput): Report
    fun remove(report: Report)
    fun ignore(report: Report)
}

@Service
@Transactional
class ReportServiceImp(
    @Autowired val reportRepository: ReportRepository,
    @Autowired val applicationEventPublisher: ApplicationEventPublisher
) : ReportService, ReportRepository by reportRepository {

    val qReport: QReport = QReport.report

    @Locked("%{#report.id}")
    override fun remove(report: Report) {
        report.status = Status.DELETED
        save(report)
        applicationEventPublisher.publishEvent(ReportNotificationEvent(report, report.targetType, report.targetId))
    }

    @Locked("%{#report.id}")
    override fun ignore(report: Report) {
        report.status = Status.IGNORED
        save(report)
    }

    @Locked("%{#user.id}-%{#input.targetId}")
    override fun create(user: User, input: ReportFrontendInput): Report {
        val report = Report()
        report.createdBy = user
        input.description?.also { report.description = it }
        input.type?.also { report.type = it }
        input.targetType?.also { report.targetType = it }
        input.content?.also { report.content = it }
        input.images.also { report.images = it }
        input.targetId.also { report.targetId = it }
        input.url.also { report.url = it }
        return save(report)
    }

    override fun findList(input: ReportBackendListInput): Page<Report> {
        var predicate = BooleanBuilder()
        input.key?.also {
            predicate = predicate.and(qReport.type.eq(input.key))
        }
        input.modelEnum?.also {
            predicate = predicate.and(qReport.targetType.eq(input.modelEnum))
        }
        input.status?.also {
            predicate = predicate.and(qReport.status.eq(input.status))
        }
        if (input.q.isNotEmpty()) {
            predicate = predicate.and(qReport.content.contains(input.q))
        }
        return findAll(predicate, input.defaultSort("-status", "-updatedAt"))
    }

}
