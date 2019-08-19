package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.SoftDeletableService
import com.boostfield.extension.list.page
import com.musicbible.mapper.movement.UpdateMovementInput
import com.musicbible.model.Movement
import com.musicbible.model.Recording
import com.musicbible.model.Release
import com.musicbible.model.Track
import com.musicbible.repository.MovementRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface MovementService : MovementRepository, SoftDeletableService<Movement> {
    override val modelName: String
        get() = "乐章"

    fun updateMovement(workId: UUID, movementId: UUID, input: UpdateMovementInput)

    /**
     * 获取相关唱片
     */
    fun relatedReleases(movementId: UUID, pageable: Pageable): Page<Release>
}

@Service
@Transactional
class MovementServiceImpl(
    @Autowired val movementRepository: MovementRepository
) : MovementService, MovementRepository by movementRepository {

    override fun updateMovement(workId: UUID, movementId: UUID, input: UpdateMovementInput) {
        val movement = findOrThrow(movementId)
        if (movement.workId != workId)
            throw AppError.BadRequest.illegalOperate(msg = "乐章[$movementId] 不属于该作品")

        input.title?.also { movement.title = it }
        input.titleCN?.also { movement.titleCN = it }
        input.order?.also { movement.order = it }
        save(movement)
    }

    override fun relatedReleases(movementId: UUID, pageable: Pageable): Page<Release> {
        val movement = findExistsOrThrow(movementId)
        val releases = movement
            .recordings.filterNot(Recording::deleted)
            .flatMap(Recording::tracks)
            .map(Track::release)
            .filterNot(Release::deleted)
            .distinct()
            .sortedByDescending(Release::createdAt)

        return releases.page(pageable)
    }
}
