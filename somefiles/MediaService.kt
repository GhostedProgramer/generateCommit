package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.BaseService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.media.CreateDimensionInput
import com.musicbible.mapper.media.CreateMediaFeatureInput
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Media
import com.musicbible.model.MediaDimension
import com.musicbible.model.MediaFeature
import com.musicbible.repository.MediaDimensionRepository
import com.musicbible.repository.MediaFeatureRepository
import com.musicbible.repository.MediaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface MediaService : TagService<Media>, MediaRepository {
    override val modelName: String
        get() = "介质"

    fun update(media: Media, input: CreateNameInput)
    fun sort(ids: List<UUID>): MutableList<Media>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

interface MediaDimensionService : MediaDimensionRepository, BaseService<MediaDimension> {
    override val modelName: String
        get() = "介质维度"

    fun updateDimension(mediaDimension: MediaDimension, input: CreateDimensionInput)
    fun add(req: CreateDimensionInput): MediaDimension
    fun create(media: Media, nameCN: String, nameEN: String? = null): MediaDimension
    fun sort(ids: List<UUID>): MutableList<MediaDimension>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

interface MediaFeatureService : MediaFeatureRepository, BaseService<MediaFeature> {
    override val modelName: String
        get() = "介质特性"

    fun updateFeature(mediaFeature: MediaFeature, input: CreateMediaFeatureInput)
    fun add(req: CreateMediaFeatureInput): MediaFeature
    fun create(dimension: MediaDimension, nameCN: String, nameEN: String? = null): MediaFeature
    fun sort(ids: List<UUID>): MutableList<MediaFeature>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
class MediaServiceImpl(
    @Autowired val mediaRepository: MediaRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : MediaService, MediaRepository by mediaRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = mediaRepository.countByIdInMediaDimension(id) +
            mediaRepository.countByIdInRelease(id)
        return result != 0L
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该介质与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun sort(ids: List<UUID>): MutableList<Media> {
        val list: MutableList<Media> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#media.id}")
    override fun update(media: Media, input: CreateNameInput) {
        input.nameCN.also { media.nameCN = it }
        input.nameEN.also { media.nameEN = it }
        input.image.also { media.image = it }
        save(media)
    }

    override fun create(nameCN: String, nameEN: String?): Media {
        val media = Media(nameCN, nameEN ?: "")
        return save(media)
    }
}

@Service
@Transactional
class MediaDimensionServiceImpl(
    @Autowired val mediaService: MediaService,
    @Autowired val mediaDimensionRepository: MediaDimensionRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : MediaDimensionService, MediaDimensionRepository by mediaDimensionRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = mediaDimensionRepository.countByIdInMediaFeature(id)
        return result != 0L
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该介质维度与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun sort(ids: List<UUID>): MutableList<MediaDimension> {
        val list: MutableList<MediaDimension> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#mediaDimension.id}")
    override fun updateDimension(mediaDimension: MediaDimension, input: CreateDimensionInput) {
        input.mediaId.also { mediaDimension.media = mediaService.findOrThrow(it) }
        input.nameCN.also { mediaDimension.nameCN = it }
        input.nameEN.also { mediaDimension.nameEN = it }
        save(mediaDimension)
    }

    override fun add(req: CreateDimensionInput) =
        save(MediaDimension(mediaService.findOrThrow(req.mediaId), req.nameCN, req.nameEN))

    override fun create(media: Media, nameCN: String, nameEN: String?): MediaDimension {
        val mediaDimension = MediaDimension(media, nameCN, nameEN ?: "")
        return save(mediaDimension)
    }
}

@Service
class MediaFeatureServiceImpl(
    @Autowired val mediaDimensionService: MediaDimensionService,
    @Autowired val mediaFeatureRepository: MediaFeatureRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : MediaFeatureService, MediaFeatureRepository by mediaFeatureRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = mediaFeatureRepository.countByIdInRelease(id)
        return result != 0L
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该介质特性与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun sort(ids: List<UUID>): MutableList<MediaFeature> {
        val list: MutableList<MediaFeature> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#mediaFeature.id}")
    override fun updateFeature(mediaFeature: MediaFeature, input: CreateMediaFeatureInput) {
        input.dimensionId.also { mediaFeature.dimension = mediaDimensionService.findOrThrow(it) }
        input.nameCN.also { mediaFeature.nameCN = it }
        input.nameEN.also { mediaFeature.nameEN = it }
        save(mediaFeature)
    }

    override fun add(req: CreateMediaFeatureInput) =
        save(MediaFeature(mediaDimensionService.findOrThrow(req.dimensionId), req.nameCN, req.nameEN))

    override fun create(dimension: MediaDimension, nameCN: String, nameEN: String?): MediaFeature {
        val mediaFeature = MediaFeature(dimension, nameCN, nameEN ?: "")
        return save(mediaFeature)
    }
}
