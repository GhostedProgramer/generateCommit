package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.util.Collection.patchCollection
import com.musicbible.aspect.Locked
import com.musicbible.event.EntityEvent
import com.musicbible.mapper.audio.UpdateAudioInput
import com.musicbible.mapper.recording.RemoveRecordingArtistInput
import com.musicbible.mapper.recording.UpdateRecordingArtistInput
import com.musicbible.mapper.recording.UpdateRecordingInput
import com.musicbible.model.Audio
import com.musicbible.model.Recording
import com.musicbible.model.RecordingArtist
import com.musicbible.model.Release
import com.musicbible.repository.AudioRepository
import com.musicbible.repository.RecordingArtistRepository
import com.musicbible.repository.RecordingRepository
import com.musicbible.repository.ReleaseRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

interface RecordingService : DocumentService<Recording>, RecordingRepository {
    override val modelName: String
        get() = "录音"

    fun updateFields(recording: Recording, fields: UpdateRecordingInput)
    fun updateArtistsAndSyncReleaseArtist(id: UUID, body: List<UpdateRecordingArtistInput>)

    /**
     * 编辑关联的艺术家，并同步到关联的唱片。
     *
     * @since 2019年8月12日, AM 11:06:46
     */
    fun updateArtistsAndSyncReleaseArtist(recording: Recording, body: List<UpdateRecordingArtistInput>)
    fun addArtistAndSyncReleaseArtist(id: UUID, body: UpdateRecordingArtistInput)


    /**
     * 录音关联一个艺术家。并且在关联成功后，同步该录音间接关联的的唱片的艺术家列表。
     *
     * @since 2019年8月12日, AM 10:40:03
     */
    fun addArtistAndSyncReleaseArtist(recording: Recording, body: UpdateRecordingArtistInput)

    /**
     * 取消关联的艺术家，并同步到关联的唱片。
     *
     * @since 2019年8月12日, AM 11:06:40
     */
    fun removeArtistAndSyncReleaseArtist(id: UUID, body: RemoveRecordingArtistInput)
    fun removeArtistAndSyncReleaseArtist(recording: Recording, body: RemoveRecordingArtistInput)
    fun copy(recording: Recording): Recording

    /**
     * 更新或新增录音的音频
     * @author smy
     */
    fun updateAudio(id: UUID, body: UpdateAudioInput): Audio

    /**
     * 删除录音的音频（仅解除关联，因为音频可能被其它录音关联)
     */
    fun deleteAudio(id: UUID)
}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class RecordingServiceImpl(
    @Lazy @Autowired val releaseService: ReleaseService,
    @Autowired val recordingRepository: RecordingRepository,
    @Autowired val recordingArtistRepository: RecordingArtistRepository,
    @Autowired val artistService: ArtistService,
    @Autowired val professionService: ProfessionService,
    @Autowired val recordingArtistService: RecordingArtistService,
    @Autowired val eventPublisher: ApplicationEventPublisher,
    @Autowired val audioRepository: AudioRepository,
    @Autowired val releaseRepository: ReleaseRepository
) : RecordingService, RecordingRepository by recordingRepository {

    @Locked("recording-%{#id}")
    override fun copy(recording: Recording): Recording {
        val new = Recording()
        new.title = recording.title
        new.titleCN = recording.titleCN
        new.duration = recording.duration
        new.recordTime = recording.recordTime
        new.work = recording.work
        new.movement = recording.movement
        new.genres = recording.genres
        new.genreIds = recording.genreIds
        new.styles = recording.styles
        new.styleIds = recording.styleIds
        new.intro = recording.intro
        val persistRecording = save(new)
        recordingArtistService.copy(recording.credits, persistRecording)
        return persistRecording
    }

    @Locked("recording-%{#id}")
    override fun softDelete(entity: Recording) {
        entity.softDelete()

        // 需要同步ReleaseArtist
        // 找出所有相关联的唱片， 并且同步这些唱片的艺术家列表。
        entity.associatedExistReleases.map(Release::id).forEach(releaseService::syncReleaseArtistFromWorkAndRecording)

        save(entity)
    }

    @Locked("recording-%{#id}")
    override fun updateFields(recording: Recording, fields: UpdateRecordingInput) {
        fields.title?.also { recording.title = it }
        fields.titleCN?.also { recording.titleCN = it }
        fields.duration?.also { recording.duration = it }
        fields.recordTime?.also { recording.recordTime = it }
        fields.intro?.also { recording.intro = it }
        save(recording)
    }

    @Locked("recording-%{#id}")
    override fun updateArtistsAndSyncReleaseArtist(id: UUID, body: List<UpdateRecordingArtistInput>) {
        val recording = findUnarchivedOrThrow(id)
        updateArtistsAndSyncReleaseArtist(recording, body)
    }

    override fun updateArtistsAndSyncReleaseArtist(recording: Recording, body: List<UpdateRecordingArtistInput>) {
        val updated = body.toMutableSet().map {
            RecordingArtist(
                recording,
                artistService.findPublishedOrThrow(it.artistId),
                professionService.findOrThrow(it.professionId),
                it.order, it.main
            )
        }
        patchCollection(
            recording.credits, updated,
            recordingArtistService::remove,
            recordingArtistService::create
        ) { origin, news ->
            origin.main = news[0].main
            origin.order = news[0].order
            recordingArtistService.save(origin)
        }
        recording.updatedAt = ZonedDateTime.now()
        val saved = save(recording)

        // 找出所有相关联的唱片， 并且同步这些唱片的艺术家列表。
        recording.associatedExistReleases.map(Release::id).forEach(releaseService::syncReleaseArtistFromWorkAndRecording)

        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    // FIXME RecordingArtist 应该由艺术家和职业唯一决定
    @Locked("%{#id}.artist")
    override fun removeArtistAndSyncReleaseArtist(id: UUID, body: RemoveRecordingArtistInput) {
        val recording = findUnarchivedOrThrow(id)
        removeArtistAndSyncReleaseArtist(recording, body)
    }

    override fun removeArtistAndSyncReleaseArtist(recording: Recording, body: RemoveRecordingArtistInput) {
        val artist = artistService.findPublishedOrThrow(body.artistId)
        val profession = professionService.findOrThrow(body.professionId)
        recordingArtistRepository.deleteByRecordingAndArtistAndProfession(recording, artist, profession)

        recording.updatedAt = ZonedDateTime.now()
        val saved = save(recording)

        // 找出所有相关联的唱片， 并且同步这些唱片的艺术家列表。
        recording.associatedExistReleases.map(Release::id).forEach(releaseService::syncReleaseArtistFromWorkAndRecording)

        eventPublisher.publishEvent(EntityEvent.updated(saved))
    }

    @Locked("recording-%{#id}")
    override fun addArtistAndSyncReleaseArtist(id: UUID, body: UpdateRecordingArtistInput) {
        val recording = findUnarchivedOrThrow(id)
        addArtistAndSyncReleaseArtist(recording, body)
    }

    override fun addArtistAndSyncReleaseArtist(recording: Recording, body: UpdateRecordingArtistInput) {
        if (recording.credits.any { it.artistId == body.artistId && it.professionId == body.professionId })
            return

        val recordingArtist = RecordingArtist(
            recording,
            artistService.findPublishedOrThrow(body.artistId),
            professionService.findOrThrow(body.professionId),
            recording.credits.size, body.main
        )
        recordingArtistService.create(recordingArtist)

        // 找出所有相关联的唱片， 并且同步这些唱片的艺术家列表。
        recording.associatedExistReleases.map(Release::id).forEach(releaseService::syncReleaseArtistFromWorkAndRecording)

        eventPublisher.publishEvent(EntityEvent.updated(recording))
    }

    @Locked("recording-%{#id}")
    override fun updateAudio(id: UUID, body: UpdateAudioInput): Audio {
        val recording = findUnarchivedOrThrow(id)
        var audio = recording.audio
        if (body.id != audio?.id)
            throw AppError.BadRequest.paramError("音频ID[${body.id}]与实际[${audio?.id}]不符")

        audio = audio ?: Audio()
        audio.url = body.url
        audio.fileName = body.fileName
        audio.size = body.size
        audio.format = body.format

        audio = audioRepository.save(audio)
        if (recording.audio != audio) {
            recording.audio = audio
            save(recording)
        }

        val release = recording.release!!
        if (!release.hasAudio) {
            release.hasAudio = true
            releaseRepository.save(release)
        }
        return audio
    }

    @Locked("recording-%{#id}")
    override fun deleteAudio(id: UUID) {
        val recording = findUnarchivedOrThrow(id)
        recording.audio = null
        save(recording)

        val release = recording.release!!
        val noneRecordingHasAudio = findAllByDeletedFalseAndRelease(release)
            .all { it.audio == null }

        if (noneRecordingHasAudio) {
            release.hasAudio = false
            releaseRepository.save(release)
        }
    }
}
