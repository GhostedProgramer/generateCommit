package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.BaseService
import com.musicbible.event.EntityEvent
import com.musicbible.model.CommonXXArtist
import com.musicbible.model.RecordingArtist
import com.musicbible.model.Release
import com.musicbible.model.ReleaseArtist
import com.musicbible.model.SourceType
import com.musicbible.model.WorkArtist
import com.musicbible.model.fromRecordingArtist
import com.musicbible.repository.ReleaseArtistRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ReleaseArtistService : BaseService<ReleaseArtist>, ReleaseArtistRepository {
    override val modelName: String
        get() = "唱片艺术家"

    fun create(releaseArtist: ReleaseArtist): ReleaseArtist

    fun remove(id: UUID)

    /**
     * 除了删除操作，还有同步数据的操作。
     */
    fun remove(releaseArtist: ReleaseArtist)

    /**
     * 后台唱片详情页面，发起的删除操作。与ReleaseArtistService#remove不同，
     * 该接口对ReleaseArtist的类型做了额外的限制。
     *
     * @since 2019年8月9日, PM 05:59:50
     */
    fun removeFromBackend(releaseArtist: ReleaseArtist)

    fun removeFromBackend(id: UUID)

    fun createFromWorkArtist(release: Release, workArtist: WorkArtist)
    fun createFromRecordingArtist(release: Release, recordingArtists: List<RecordingArtist>)
    fun setAsMain(id: UUID, boolean: Boolean)
    fun setAsMain(ids: List<UUID>, boolean: Boolean)
}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class ReleaseArtistServiceImpl(
    @Autowired val releaseArtistRepository: ReleaseArtistRepository,
    @Autowired val artistService: ArtistService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val recordingArtistService: RecordingArtistService
) : ReleaseArtistService, ReleaseArtistRepository by releaseArtistRepository {

    override fun removeFromBackend(id: UUID) {
        val releaseArtist = findOrThrow(id)
        removeFromBackend(releaseArtist)
    }

    override fun removeFromBackend(releaseArtist: ReleaseArtist) {
        val sourceType = releaseArtist.sourceType
        if (sourceType == SourceType.SYNC_FROM_WORK) {
            throw AppError.BadRequest.default(msg = "无法删除作曲家")
        }
        if (sourceType == SourceType.SYNC_FROM_RECORDING) {
            if (releaseArtist.sources.isNotEmpty()) {
                val recordingArtistIds = releaseArtist.sources.map(UUID::fromString)
                recordingArtistIds.forEach(recordingArtistService::deleteById)
            }
        }

        remove(releaseArtist)
    }

    override fun setAsMain(ids: List<UUID>, boolean: Boolean) {
        ids.forEach {
            setAsMain(it, boolean)
        }
    }

    override fun setAsMain(id: UUID, boolean: Boolean) {
        val releaseArtist = findOrThrow(id)
        releaseArtist.main = boolean
        save(releaseArtist)
    }

    override fun createFromRecordingArtist(release: Release, recordingArtists: List<RecordingArtist>) {
        if (recordingArtists.isEmpty()) return

        val list = recordingArtists.map(::fromRecordingArtist)
        val map = HashMap<CommonXXArtist, Array<String>>()
        for (t in list) {
            val id = (t.target as RecordingArtist).id.toString()
            val strings = map.get(t)
            if (strings != null) {
                map[t] = strings.plus(id)
            } else {
                map[t] = arrayOf(id)
            }
        }

        map.forEach { k, v ->
            val releaseArtist = ReleaseArtist(release, k.artist)
            releaseArtist.profession = (k.target as RecordingArtist).profession
            releaseArtist.sourceType = SourceType.SYNC_FROM_RECORDING
            releaseArtist.sources = v
            releaseArtist.main = false
            create(releaseArtist)
        }

    }

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun createFromWorkArtist(release: Release, workArtist: WorkArtist) {
        val releaseArtist = ReleaseArtist(release, workArtist.artist)
        releaseArtist.profession = workArtist.profession
        releaseArtist.sourceType = SourceType.SYNC_FROM_WORK
        releaseArtist.order = 1
        releaseArtist.main = true
        create(releaseArtist)
    }

    override fun create(releaseArtist: ReleaseArtist): ReleaseArtist {
        val saved = save(releaseArtist)
        eventPublisher.publishEvent(
            EntityEvent.created(releaseArtist, mapOf("artistId" to releaseArtist.artistId))
        )
        logger.debug("Create a new ReleaseArtist[{}]", saved.id)

        return saved
    }

    override fun remove(releaseArtist: ReleaseArtist) {
        delete(releaseArtist)
        eventPublisher.publishEvent(
            EntityEvent.deleted(releaseArtist, mapOf("artistId" to releaseArtist.artistId))
        )
        logger.debug("ReleaseArtist[{}] deleted", releaseArtist.id)
    }

    override fun remove(id: UUID) {
        val releaseArtist = findOrThrow(id)
        remove(releaseArtist)
    }
}
