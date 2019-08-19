package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.mapper.catalog.CatalogMapper
import com.musicbible.model.Appreciation
import com.musicbible.model.Artist
import com.musicbible.model.Label
import com.musicbible.model.Recording
import com.musicbible.model.Release
import com.musicbible.model.Sale
import com.musicbible.model.Video
import com.musicbible.model.Work
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 统一的实体映射服务，适用于动态关联实体的场景，如评论、收藏
 */
interface EntityMapService {
    fun toIdentity(entity: Any): Map<String, Any>
}

@Service
class EntityMapServiceImpl(
    @Autowired val catalogMapper: CatalogMapper
) : EntityMapService {
    override fun toIdentity(entity: Any): Map<String, Any> {
        return when (entity) {
            is Release -> mapOf(
                "id" to entity.id,
                "title1" to entity.title1,
                "titleCN" to entity.titleCN,
                "images" to entity.images,
                "catalogs" to entity.catalogs.map {
                    catalogMapper.toReleaseCatalogDetailOutput(it)
                },
                "collectCount" to entity.collectCount,
                "commentCount" to entity.commentCount,
                "deleted" to entity.deleted
            )
            is Artist -> mapOf(
                "id" to entity.id,
                "lastName" to entity.lastName,
                "firstName" to entity.firstName,
                "abbrCN" to entity.abbrCN,
                "abbr" to entity.abbr,
                "nameCN" to entity.nameCN,
                "images" to entity.images,
                "deleted" to entity.deleted
            )
            is Work -> mapOf(
                "id" to entity.id,
                "title" to entity.title,
                "titleCN" to entity.titleCN,
                "catalogNumbers" to entity.catalogNumbers,
                "deleted" to entity.deleted
            )
            is Sale -> mapOf(
                "id" to entity.id,
                "title1" to entity.release!!.title1,
                "titleCN" to entity.release!!.titleCN,
                "images" to entity.images,
                "deleted" to entity.deleted
            )
            is Video -> mapOf(
                "id" to entity.id,
                "name" to entity.name,
                "images" to entity.images,
                "collectCount" to entity.collectCount,
                "commentCount" to entity.commentCount,
                "likeCount" to entity.likeCount,
                "duration" to entity.duration,
                "labels" to entity.labels,
                "deleted" to entity.deleted
            )
            is Appreciation -> mapOf(
                "id" to entity.id,
                "title" to entity.title,
                "digest" to entity.digest,
                "targetType" to entity.targetType,
                "targetId" to entity.targetId,
                "likeCount" to entity.likeCount,
                "commentCount" to entity.commentCount,
                "deleted" to entity.deleted
            )
            is Label -> mapOf(
                "id" to entity.id,
                "name" to entity.name,
                "nameCN" to entity.nameCN,
                "images" to entity.images
            )
            is Recording -> mapOf(
                "id" to entity.id,
                "title" to entity.title,
                "titleCN" to entity.titleCN
            )
            else -> throw AppError.BadRequest.default(msg = "Unsupported type ${entity::class}")
        }
    }
}

