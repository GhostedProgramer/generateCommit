package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.payload.PageQuery
import com.boostfield.spring.service.SoftDeletableService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.billboard.BillboardInput
import com.musicbible.mapper.billboard.BillboardReleaseListInput
import com.musicbible.mapper.release.ReleaseFilterInput
import com.musicbible.model.Billboard
import com.musicbible.model.BillboardReleasePK
import com.musicbible.model.Release
import com.musicbible.repository.BillboardRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface BillboardService : SoftDeletableService<Billboard>, BillboardRepository {
    override val modelName: String
        get() = "榜单"

    fun updateImages(id: UUID, images: Array<String>)
    fun updateFields(id: UUID, input: BillboardInput)
    fun associateRelease(id: UUID, releaseId: UUID)
    fun unAssociateRelease(id: UUID, releaseId: UUID)
    fun getRankOrderedRelease(id: UUID, count: Int = 0): List<Release>
    fun getRankOrderedReleaseByPage(id: UUID, input: BillboardReleaseListInput): Page<Release>
    fun getUnRankReleaseStartWithByPage(id: UUID, prefix: String, pageQuery: PageQuery): Page<Release>
    fun searchBillboardReleaseByPage(id: UUID, input: BillboardReleaseListInput): Page<Release>
    fun swapReleaseRank(id: UUID, fromId: UUID, toId: UUID)
    fun freshReleaseCount(billboard: Billboard)
    fun getReleaseFirstLetters(id: UUID): List<String>
    fun rebuildReleaseAssociate(release: Release, billboardIds: Array<UUID>)
    fun delete(id: UUID)
}

@Service
@Transactional
class BillboardServiceImpl(
    @Autowired val billboardRepository: BillboardRepository,
    @Autowired val billboardReleaseService: BillboardReleaseService,
    @Autowired @PersistenceContext val em: EntityManager,
    @Autowired @Lazy val releaseService: ReleaseService
) : BillboardService, BillboardRepository by billboardRepository {
    @Locked("%{#id}")
    override fun updateImages(id: UUID, images: Array<String>) {
        val billboard = findExistsOrThrow(id)
        billboard.images = images
        save(billboard)
    }

    @Locked("%{#id}")
    override fun updateFields(id: UUID, input: BillboardInput) {
        val billboard = findExistsOrThrow(id)
        billboard.nameCN = input.nameCN ?: billboard.nameCN
        billboard.name = input.name ?: billboard.name
        updatedRank(billboard, input.hasRank)
        billboard.intro = input.intro ?: billboard.intro
        input.commendLevel?.also { billboard.commendLevel = it }
        save(billboard)
    }

    private fun updatedRank(billboard: Billboard, hasRank: Boolean?) {
        billboard.hasRank = hasRank ?: billboard.hasRank
        if (billboard.hasRank && billboardReleaseService.getNextRank(billboard.id) == 1) {
            billboardReleaseService.initAllReleaseRank(billboard.id)
        }
    }

    override fun freshReleaseCount(billboard: Billboard) {
        billboard.releaseCount = billboardReleaseService.countByBillboard(billboard)
        save(billboard)
    }

    @Locked("%{#id}")
    override fun delete(id: UUID) {
        softDelete(id)
    }

    @Locked("%{#id}-%{#releaseId}")
    override fun associateRelease(id: UUID, releaseId: UUID) {
        val billboard = findExistsOrThrow(id)
        val release = releaseService.findExistsOrThrow(releaseId)
        // 不管有没有hasRank, 都去增加
        val nextRank = billboardReleaseService.getNextRank(id)
        billboardReleaseService.create(billboard, release, nextRank)
        freshReleaseCount(billboard)
    }

    override fun rebuildReleaseAssociate(release: Release, billboardIds: Array<UUID>) {
        billboardReleaseService.deleteAll(billboardReleaseService.findByRelease(release))
        for (billboardId in billboardIds) {
            associateRelease(billboardId, release.id)
        }
    }

    @Locked("%{#id}-%{#releaseId}")
    override fun unAssociateRelease(id: UUID, releaseId: UUID) {
        val billboard = findExistsOrThrow(id)
        billboardReleaseService.delete(id, releaseId)
        freshReleaseCount(billboard)
    }

    override fun getRankOrderedRelease(id: UUID, count: Int): List<Release> {
        return if (count == 0) {
            billboardReleaseService.getAllReleaseOrderByRankAsc(id)
        } else {
            billboardReleaseService.getNReleaseOrderByRankAsc(id, count)
        }.map { it.release }
    }

    override fun getRankOrderedReleaseByPage(id: UUID, input: BillboardReleaseListInput): Page<Release> {
        return billboardReleaseService.getPagedReleaseOrderByRankAsc(id, input)
            .map { it.release }
    }

    override fun getUnRankReleaseStartWithByPage(id: UUID, prefix: String, pageQuery: PageQuery): Page<Release> {
        return billboardReleaseService.getPagedReleaseUnorderByFirstLetter(id, prefix, pageQuery)
            .map { it.release }
    }

    override fun searchBillboardReleaseByPage(id: UUID, input: BillboardReleaseListInput): Page<Release> {
        val releaseInput = ReleaseFilterInput().also {
            it.billboardIds = mutableListOf(id)
            it.q = input.q
            it.firstLetter = input.letter?.toUpperCase()
            it.sort = input.sort
            it.page = input.page
            it.size = input.size
        }
        val hits = releaseService.frontendListSearch(releaseInput)
        val result = releaseService.findAllById(hits.content.map { it.id })
        return PageImpl<Release>(result, hits.pageable, hits.totalElements)
    }

    @Locked("%{#id}-%{#fromId}-%{#toId}")
    override fun swapReleaseRank(id: UUID, fromId: UUID, toId: UUID) {
        val from = billboardReleaseService.findById(BillboardReleasePK(id, fromId))
            .orElseThrow { AppError.NotFound.default(msg = "找不到榜单id为：${id}下对应的id为${fromId}的唱片") }
        val to = billboardReleaseService.findById(BillboardReleasePK(id, toId))
            .orElseThrow { AppError.NotFound.default(msg = "找不到榜单id为：${id}下对应的id为${toId}的唱片") }
        billboardReleaseService.swapRank(from, to)
    }

    override fun getReleaseFirstLetters(id: UUID): List<String> {
        return billboardReleaseService.aggregateReleaseFirstLetter(id)
    }
}
