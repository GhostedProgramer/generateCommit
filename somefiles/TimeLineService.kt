package com.musicbible.service

import com.boostfield.spring.service.SoftDeletableService
import com.musicbible.model.QTimeLine
import com.musicbible.model.TimeLine
import com.musicbible.repository.TimeLineRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface TimeLineService : TimeLineRepository, SoftDeletableService<TimeLine> {
    override val modelName: String
        get() = "动态"

    fun page(pageable: Pageable): Page<TimeLine>

}

@Service
@Transactional
class TimeLineServiceImpl(
    @Autowired @Lazy val releaseService: ReleaseService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val timeLineRepository: TimeLineRepository
) : TimeLineService, TimeLineRepository by timeLineRepository {
    val qTimeLine = QTimeLine.timeLine

    override fun page(pageable: Pageable): Page<TimeLine> {
        val predicate = qTimeLine.deleted.isFalse
        return findAll(predicate, pageable)
    }

}
