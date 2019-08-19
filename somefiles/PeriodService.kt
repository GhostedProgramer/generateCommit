package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.aspect.Locked
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Period
import com.musicbible.repository.PeriodRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PeriodService : TagService<Period>, PeriodRepository {
    override val modelName: String
        get() = "时期"

    fun update(period: Period, input: CreateNameInput)
    fun sort(ids: List<UUID>): MutableList<Period>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class PeriodServiceImpl(
    @Autowired val periodRepository: PeriodRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : PeriodService, PeriodRepository by periodRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = periodRepository.countByIdInArtist(id) +
            periodRepository.countByIdInWork(id) +
            periodRepository.countByIdInRelease(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<Period> {
        val list: MutableList<Period> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#period.id}")
    override fun update(period: Period, input: CreateNameInput) {
        input.nameCN.also { period.nameCN = it }
        input.nameEN.also { period.nameEN = it }
        save(period)
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该时期与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun create(nameCN: String, nameEN: String?): Period {
        val period = Period(nameCN, nameEN ?: "")
        return save(period)
    }
}

