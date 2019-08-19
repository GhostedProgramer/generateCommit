package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.event.EntityEvent
import com.musicbible.model.WorkArtist
import com.musicbible.repository.WorkArtistRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface WorkArtistService : BaseService<WorkArtist>, WorkArtistRepository {
    override val modelName: String
        get() = "作品艺术家"

    fun create(workArtist: WorkArtist): WorkArtist

    fun remove(workArtist: WorkArtist)
}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class WorkArtistServiceImpl(
    @Autowired val workArtistRepository: WorkArtistRepository,
    @Autowired val artistService: ArtistService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : WorkArtistService, WorkArtistRepository by workArtistRepository {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun create(workArtist: WorkArtist): WorkArtist {
        val saved = save(workArtist)
        eventPublisher.publishEvent(
            EntityEvent.created(saved, mapOf("artistId" to workArtist.artistId))
        )
        logger.info("Create a new WorkArtist[{}]", saved.id)

        return saved
    }

    override fun remove(workArtist: WorkArtist) {
        delete(workArtist)
        eventPublisher.publishEvent(
            EntityEvent.deleted(workArtist, mapOf("artistId" to workArtist.artistId))
        )
        logger.info("WorkArtist[{}] deleted", workArtist.id)
    }
}
