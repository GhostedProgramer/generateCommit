package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.event.EntityEvent
import com.musicbible.model.Recording
import com.musicbible.model.RecordingArtist
import com.musicbible.repository.RecordingArtistRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface RecordingArtistService : BaseService<RecordingArtist>, RecordingArtistRepository {
    override val modelName: String
        get() = "录音艺术家"

    fun create(recordingArtist: RecordingArtist): RecordingArtist

    fun remove(recordingArtist: RecordingArtist)

    /**
     * 复制一组RecordingArtist， 并将recording指向新的Recording
     */
    fun copy(credits: List<RecordingArtist>, recording: Recording)
}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class RecordingArtistServiceImpl(
    @Autowired val recordingArtistRepository: RecordingArtistRepository,
    @Autowired val artistService: ArtistService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : RecordingArtistService, RecordingArtistRepository by recordingArtistRepository {

    override fun copy(credits: List<RecordingArtist>, recording: Recording) {
        if (credits.isNotEmpty()) {
            val transientCredits = credits.map { it.clone(recording) }
            saveAll(transientCredits)
        }
    }

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun create(recordingArtist: RecordingArtist): RecordingArtist {
        val saved = save(recordingArtist)
        eventPublisher.publishEvent(
            EntityEvent.created(recordingArtist, mapOf("artistId" to recordingArtist.artistId))
        )
        logger.debug("Create a new RecordingArtist[{}]", saved.id)

        return saved
    }

    override fun remove(recordingArtist: RecordingArtist) {
        delete(recordingArtist)
        eventPublisher.publishEvent(
            EntityEvent.deleted(recordingArtist, mapOf("artistId" to recordingArtist.artistId))
        )
        logger.debug("RecordingArtist[{}] deleted", recordingArtist.id)
    }
}
