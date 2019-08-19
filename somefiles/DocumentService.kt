package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.repository.base.DocumentRepository
import com.boostfield.spring.service.DraftableService
import com.musicbible.model.Document
import com.musicbible.model.User
import java.util.*

interface DocumentService<T : Document> : DraftableService<T>, DocumentRepository<T> {
    fun findUnarchivedOrThrow(id: UUID): T {
        return findExistsOrThrow(id).apply { mustNotArchived() }
    }

    fun findCheckingOrThrow(id: UUID): T {
        return findExistsOrThrow(id).apply { mustChecking() }
    }

    fun findExistsAndOwnOrThrow(id: UUID, owner: User): T {
        val video = super.findExistsOrThrow(id)
        val createdBy = video.createdBy
        if (createdBy != null && createdBy.id == owner.id) {
            return video
        }
        throw AppError.BadRequest.default(msg = "无法操作其他人对象")
    }

    fun getPrevVersion(document: T) = document
        .originId
        ?.let { findByOriginIdAndVersion(it, document.version - 1) }

    fun getOriginVersion(document: T): T = document
        .originId
        ?.let(::findOrThrow)
        ?: document
}
