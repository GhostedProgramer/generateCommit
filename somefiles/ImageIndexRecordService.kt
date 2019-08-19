package com.musicbible.service

import com.musicbible.model.ImageIndexRecord
import com.musicbible.model.Release
import com.musicbible.repository.ImageIndexRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface ImageIndexRecordService : ImageIndexRecordRepository {
    fun recordDelete(releaseId: UUID, path: String)

    fun recordIndex(releaseId: UUID, path: String)
}

@Service
class ImageIndexRecordServiceImpl(
    @Autowired val imageIndexRecordRepository: ImageIndexRecordRepository,
    @Autowired @PersistenceContext val em: EntityManager
) : ImageIndexRecordService, ImageIndexRecordRepository by imageIndexRecordRepository {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun recordDelete(releaseId: UUID, path: String) {
        val release = em.getReference(Release::class.java, releaseId)

        val imageIndexRecord =
            imageIndexRecordRepository.findByReleaseAndPath(release, path) ?: ImageIndexRecord(release, path)
        imageIndexRecord.softDelete()
        save(imageIndexRecord)
    }

    override fun recordIndex(releaseId: UUID, path: String) {
        val release = em.getReference(Release::class.java, releaseId)

        val imageIndexRecord =
            imageIndexRecordRepository.findByReleaseAndPath(release, path) ?: ImageIndexRecord(release, path)
        imageIndexRecord.recall()
        try {
            save(imageIndexRecord)
        } catch (ex: DataIntegrityViolationException) {
            logger.warn("Save ImageIndexRecord encounter unique violation, release: {}, path: {}",
                releaseId, path)
        }
    }
}
