package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.exception.TrackGroupException
import com.musicbible.model.Release
import com.musicbible.model.TrackGroup
import com.musicbible.repository.TrackGroupRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface TrackGroupService : TrackGroupRepository, BaseService<TrackGroup> {
    override val modelName: String
        get() = "音轨组"

    fun create(release: Release): TrackGroup
    fun copy(origin: TrackGroup, newRelease: Release): TrackGroup
    fun getFirstTrackGroup(master: Release): TrackGroup
    fun getSelfTrackGroup(release: Release): TrackGroup?
    fun clearRelation(subject: Release, master: Release)
    fun mergeTrackGroup(trackGroups: MutableList<TrackGroup>): Pair<TrackGroup, MutableList<TrackGroup>>
    fun updateTrackGroupOrSubjectsFetched(trackGroup: TrackGroup, fetched: Boolean): List<TrackGroup>
}

/**
 * 警告：当录音关联的艺术家或作品关联的艺术家发生变化时，一定要注意是否需要同步到唱片艺术家列表。
 * 否则就会导致唱片的艺术家列表数据不一致。
 */
@Service
@Transactional
class TrackGroupServiceImpl(
    @Autowired val trackGroupRepository: TrackGroupRepository,
    @Autowired val trackService: TrackService,
    @Lazy @Autowired val releaseService: ReleaseService,
    @Autowired @PersistenceContext val em: EntityManager
) : TrackGroupService, TrackGroupRepository by trackGroupRepository {

    override fun create(release: Release): TrackGroup {
        if (release.beMaster) {
            if (release.trackGroups.isNotEmpty())
                throw TrackGroupException("主版本只应有一个TrackGroup")
        } else {
            val trackGroup = getSelfTrackGroup(release)
            if (trackGroup != null)
                throw TrackGroupException("子版本只允许有一个自己的trackGroup")
        }
        return save(TrackGroup(release).also { it.order = release.nextTrackGroupOrder })
    }

    /**
     * 把master中的track拷贝到subjectRelease的trackGroup中
     */
    override fun copy(origin: TrackGroup, newRelease: Release): TrackGroup {
        val freshRelease = releaseService.findOrThrow(newRelease.id)
        // 构造一个subjectRelease的trackGroup
        val trackGroup = save(TrackGroup(freshRelease).also { it.order = freshRelease.nextTrackGroupOrder })
        em.flush()
        // 利用subjectRelease和subjectTrackGroup作为元信息拷贝所有tracks
        val newTracks = trackService.copyAndPersist(origin.tracks, freshRelease, trackGroup)
        trackGroup.tracks = newTracks
        trackGroup.fetched = true

        return save(trackGroup)
    }

    override fun getFirstTrackGroup(master: Release): TrackGroup {
        if (!master.beMaster) throw TrackGroupException("release 不是主版本")
        if (master.trackGroups.size > 1) throw TrackGroupException("主版本只应有一个TrackGroup")
        var trackGroup = master.trackGroups.firstOrNull()
        if (trackGroup == null) {
            trackGroup = create(master)
            master.trackGroups.add(trackGroup)
        }
        return trackGroup
    }

    override fun getSelfTrackGroup(release: Release): TrackGroup? {
        if (release.beMaster) throw TrackGroupException("主版本只有一个trackGroup，请使用getFirstTrackGroup()")
        if (release.trackGroups.isEmpty()) return null
        return try {
            release.trackGroups.single { it.master == null }
        } catch (e: NoSuchElementException) {
            null
        } catch (e: IllegalArgumentException) {
            throw TrackGroupException("该子版本有多个selfTrackGroup")
        }
    }

    override fun clearRelation(subject: Release, master: Release) {
        val masterTrackGroup = getFirstTrackGroup(master)
        var subjectTrackGroup: TrackGroup? = null
        for (trackGroup in subject.trackGroups) {
            if (trackGroup.master != null) {
                if (trackGroup.master!!.id == masterTrackGroup.id) {
                    trackGroup.master = null
                    subjectTrackGroup = save(trackGroup)
                    subject.trackGroups.remove(trackGroup)
                    break
                }
            }
        }
        subjectTrackGroup?.also {
            trackService.deleteAll(it.tracks)
            delete(it)
        }
    }

    /**
     * 只有存在多个trackGroup的情况下，才需要去merge
     * trackGroup合并的规则是将有继承的trackGroup合并到自己的trackGroup
     */
    override fun mergeTrackGroup(trackGroups: MutableList<TrackGroup>): Pair<TrackGroup, MutableList<TrackGroup>> {
        if (trackGroups.size <= 1) {
            throw TrackGroupException("只有存在多个trackGroup的情况下，才需要去merge")
        }
//        trackGroups.find(TrackGroup::isMain)
        val mainTrackGroup = trackGroups.single { it.master == null }
        for (trackGroup in trackGroups) {
            if (trackGroup == mainTrackGroup) continue
            trackGroup.tracks.forEach { it.trackGroup = mainTrackGroup }
            trackService.saveAll(trackGroup.tracks)
        }
        deleteAll(trackGroups.filter { it.master != null })
        trackGroups.remove(mainTrackGroup)
        return Pair(save(mainTrackGroup), trackGroups)
    }

    override fun updateTrackGroupOrSubjectsFetched(trackGroup: TrackGroup, fetched: Boolean): List<TrackGroup> {
        val trackGroups =
            if (trackGroup.master == null && trackGroup.fetched == null) {
                trackGroup.subjects
            } else {
                mutableListOf(trackGroup)
            }
        trackGroups.forEach { it.fetched = false }
        return saveAll(trackGroups)
    }
}
