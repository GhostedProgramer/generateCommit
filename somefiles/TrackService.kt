package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.model.Release
import com.musicbible.model.Track
import com.musicbible.model.TrackGroup
import com.musicbible.repository.TrackRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface TrackService : BaseService<Track>, TrackRepository {
    override val modelName: String
        get() = "音轨"

    fun deleteAllByRelease(subVersion: Release)

    fun copyAndPersist(origins: List<Track>, release: Release, trackGroup: TrackGroup): MutableList<Track>
}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class TrackServiceImpl(
    @Autowired val trackRepository: TrackRepository,
    @Autowired val recordingService: RecordingService,
    @Lazy @Autowired val releaseService: ReleaseService
) : TrackService, TrackRepository by trackRepository {
    override fun copyAndPersist(origins: List<Track>, release: Release, trackGroup: TrackGroup): MutableList<Track> {
        val newTracks = mutableListOf<Track>()
        for (origin in origins) {
            val track =
                Track(
                    release,
                    recordingService.copy(origin.recording),
                    trackGroup,
                    origin.order,
                    origin.side
                )
            newTracks.add(save(track))
        }
        return newTracks
    }

    override fun deleteAllByRelease(subVersion: Release) {
        val tracks = findByRelease(subVersion)
        if (tracks.isNotEmpty()) {
            deleteAll(tracks)
        }

        releaseService.syncReleaseArtistFromWorkAndRecording(subVersion.id)
    }
}
