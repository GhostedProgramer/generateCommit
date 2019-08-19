package com.musicbible.service

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.literature.BackendLiteratureListingOutput
import com.musicbible.mapper.literature.LiteratureBackendListInput
import com.musicbible.mapper.literature.LiteratureMapper
import com.musicbible.mapper.literature.UpdateLiteratureImageInput
import com.musicbible.mapper.literature.UpdateLiteratureInput
import com.musicbible.model.Literature
import com.musicbible.model.QLiterature
import com.musicbible.repository.LiteratureRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface LiteratureService :
    TimeKeepingService<Literature>, LiteratureRepository<Literature> {
    override val modelName: String
        get() = "音乐圣经微信公众号"

    fun updateImages(literature: Literature, body: UpdateLiteratureImageInput)
    fun updateFields(literature: Literature, fields: UpdateLiteratureInput)
    fun findList(input: LiteratureBackendListInput): PageResponse<BackendLiteratureListingOutput>
    fun updateRelateRelease(id: UUID, releaseIds: MutableSet<UUID>)
}

@Service
@Transactional
class LiteratureServiceImp(
    @Autowired val literatureRepository: LiteratureRepository<Literature>,
    @Autowired val literatureMapper: LiteratureMapper,
    @Autowired val releaseService: ReleaseService
) : LiteratureService, LiteratureRepository<Literature> by literatureRepository {
    val qLiterature: QLiterature = QLiterature.literature

    override fun findList(input: LiteratureBackendListInput): PageResponse<BackendLiteratureListingOutput> {
        val result: PageResponse<BackendLiteratureListingOutput>
        if (input.q == null) {
            result = findAll(input.defaultSortByCreateAt())
                .map(literatureMapper::toBackendLiteratureListingOutput)
                .let(RestResponse::page)
        } else {
            result = findAll(qLiterature.name.contains("${input.q}"), input.defaultSortByCreateAt())
                .map(literatureMapper::toBackendLiteratureListingOutput)
                .let(RestResponse::page)
        }
        return result
    }

    @Locked("%{#literature.id}")
    override fun updateImages(literature: Literature, body: UpdateLiteratureImageInput) {
        body.image?.also { literature.image = it }
        save(literature)
    }

    @Locked("%{#literature.id}")
    override fun updateFields(literature: Literature, fields: UpdateLiteratureInput) {
        fields.name?.also { literature.name = it }
        fields.relatedSite?.also { literature.relatedSite = it }
        save(literature)
    }

    @Locked("%{#id}")
    override fun updateRelateRelease(id: UUID, releaseIds: MutableSet<UUID>) {
        val literature = findOrThrow(id)
        literature.releases = releaseIds.map(releaseService::findUnarchivedOrThrow).toMutableSet()
        literature.releaseCount = releaseIds.size.toLong()
        save(literature)
    }


}
