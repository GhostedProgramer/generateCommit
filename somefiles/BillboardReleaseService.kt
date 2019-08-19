package com.musicbible.service

import com.boostfield.extension.string.firstLetter
import com.boostfield.spring.payload.PageQuery
import com.musicbible.model.Billboard
import com.musicbible.model.BillboardRelease
import com.musicbible.model.BillboardReleasePK
import com.musicbible.model.QBillboardRelease
import com.musicbible.model.QRelease
import com.musicbible.model.Release
import com.musicbible.repository.BillboardReleaseRepository
import com.querydsl.core.types.Order
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQuery
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

interface BillboardReleaseService : BillboardReleaseRepository {
    fun getNextRank(billboardId: UUID): Int
    fun create(billboard: Billboard, release: Release, rank: Int): BillboardRelease
    fun delete(billboardId: UUID, releaseId: UUID)
    fun getAllReleaseOrderByRankAsc(billboardId: UUID): List<BillboardRelease>
    fun getNReleaseOrderByRankAsc(billboardId: UUID, n: Int): List<BillboardRelease>
    fun getPagedReleaseOrderByRankAsc(billboardId: UUID, pageQuery: PageQuery): Page<BillboardRelease>
    fun getPagedReleaseUnorderByFirstLetter(billboardId: UUID, letter: String, pageQuery: PageQuery): Page<BillboardRelease>
    fun initAllReleaseRank(billboardId: UUID)
    fun swapRank(from: BillboardRelease, to: BillboardRelease)
    fun aggregateReleaseFirstLetter(billboardId: UUID): List<String>
    fun findByRelease(release: Release): MutableIterable<BillboardRelease>
}

@Service
@Transactional
class BillboardReleaseServiceImpl(
    @Autowired val billboardReleaseRepository: BillboardReleaseRepository,
    @Autowired @PersistenceContext val em: EntityManager
) : BillboardReleaseService, BillboardReleaseRepository by billboardReleaseRepository {

    val billboardRelease: QBillboardRelease = QBillboardRelease.billboardRelease

    override fun findByRelease(release: Release): MutableIterable<BillboardRelease> {
        return findAll(billboardRelease.release.eq(release))
    }

    override fun getNextRank(billboardId: UUID): Int {
        val last = getAllReleaseOrderByRankAsc(billboardId).firstOrNull()?.rank ?: 0
        return last + 1
    }

    override fun create(billboard: Billboard, release: Release, rank: Int): BillboardRelease {
        val id = BillboardReleasePK(billboard.id, release.id)
        return save(BillboardRelease(id, billboard, release, rank))
    }

    override fun delete(billboardId: UUID, releaseId: UUID) {
        findOne(billboardRelease.billboard.id.eq(billboardId)
            .and(billboardRelease.release.id.eq(releaseId)))
            .also {
                if (it.isPresent) {
                    delete(it.get())
                }
            }
    }

    override fun getAllReleaseOrderByRankAsc(billboardId: UUID): List<BillboardRelease> {
        val criteria = billboardRelease.billboard.id.eq(billboardId)
        return findAll(criteria,
            OrderSpecifier(Order.ASC, billboardRelease.rank)).toList()
    }

    override fun getNReleaseOrderByRankAsc(billboardId: UUID, n: Int): List<BillboardRelease> {
        return findByBillboardIdOrderByRankAsc(billboardId, PageRequest.of(0, n)).content

    }

    override fun getPagedReleaseOrderByRankAsc(billboardId: UUID, pageQuery: PageQuery): Page<BillboardRelease> {
        return findByBillboardIdOrderByRankAsc(billboardId, pageQuery.pageable())
    }

    override fun getPagedReleaseUnorderByFirstLetter(billboardId: UUID, letter: String, pageQuery: PageQuery): Page<BillboardRelease> {
        val criteria = billboardRelease.billboard.id.eq(billboardId)
            .and(billboardRelease.release.title1.startsWithIgnoreCase(letter))
            .and(billboardRelease.release.published.eq(true))
        return findAll(criteria, pageQuery.pageable())
    }

    override fun initAllReleaseRank(billboardId: UUID) {
        var idx = 1
        getAllReleaseOrderByRankAsc(billboardId).map {
            it.rank = idx
            idx = idx + 1
            it
        }.let {
            saveAll(it)
        }
    }

    override fun swapRank(from: BillboardRelease, to: BillboardRelease) {
        val swap = from.rank
        from.rank = to.rank
        to.rank = swap
        saveAll(listOf(from, to))
    }

    override fun aggregateReleaseFirstLetter(billboardId: UUID): List<String> {
        val release = QRelease.release
        return JPAQuery<String>(em)
            .from(billboardRelease)
            .innerJoin(release)
            .on(release.id.eq(billboardRelease.release.id))
            .where(billboardRelease.billboard.id.eq(billboardId))
            .select(release.title1)
            .fetch()
            .map { it.firstLetter().toString() }
            .distinct()
            .sorted()
    }

    private fun findByBillboardIdOrderByRankAsc(
        billboardId: UUID,
        pageable: Pageable,
        releasePublished: Boolean? = true
    ): Page<BillboardRelease> {
        val billboardRelease = QBillboardRelease.billboardRelease
        var criteria = billboardRelease.billboard.id.eq(billboardId)
        releasePublished?.also {
            criteria = criteria.and(
                billboardRelease.release.published.eq(it)
            )
        }
        return findAll(criteria, PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.Direction.ASC, "rank"))
    }

}
