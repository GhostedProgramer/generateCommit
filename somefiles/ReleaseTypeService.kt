package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.aspect.Locked
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.ReleaseType
import com.musicbible.repository.ReleaseTypeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ReleaseTypeService : ReleaseTypeRepository, TagService<ReleaseType> {
    override val modelName: String
        get() = "唱片类型"

    fun update(releaseType: ReleaseType, input: CreateNameInput)
    fun sort(ids: List<UUID>): MutableList<ReleaseType>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class ReleaseTypeServiceImpl(
    @Autowired val releaseTypeRepository: ReleaseTypeRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : ReleaseTypeService, ReleaseTypeRepository by releaseTypeRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = releaseTypeRepository.countByIdInRelease(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<ReleaseType> {
        val list: MutableList<ReleaseType> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#releaseType.id}")
    override fun update(releaseType: ReleaseType, input: CreateNameInput) {
        input.nameCN.also { releaseType.nameCN = it }
        input.nameEN.also { releaseType.nameEN = it }
        save(releaseType)
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该唱片属性与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun create(nameCN: String, nameEN: String?): ReleaseType {
        val releaseType = ReleaseType(nameCN, nameEN ?: "")
        return save(releaseType)
    }
}
