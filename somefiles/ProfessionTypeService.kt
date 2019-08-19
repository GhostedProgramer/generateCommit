package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.aspect.Locked
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.ProfessionType
import com.musicbible.repository.ProfessionTypeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ProfessionTypeService : ProfessionTypeRepository, TagService<ProfessionType> {
    override val modelName: String
        get() = "职业类别"

    fun updateType(professionType: ProfessionType, input: CreateNameInput)
    fun sort(ids: List<UUID>): MutableList<ProfessionType>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class ProfessionTypeServiceImpl(
    @Autowired val professionTypeRepository: ProfessionTypeRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : ProfessionTypeService, ProfessionTypeRepository by professionTypeRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = professionTypeRepository.countByIdInProfession(id)
        return result != 0L
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该职业类型与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun sort(ids: List<UUID>): MutableList<ProfessionType> {
        val list: MutableList<ProfessionType> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    override fun create(nameCN: String, nameEN: String?): ProfessionType {
        val type = ProfessionType(nameCN, nameEN ?: "")
        return save(type)
    }

    @Locked("%{#professionType.id}")
    override fun updateType(professionType: ProfessionType, input: CreateNameInput) {
        input.nameCN.also { professionType.nameCN = it }
        input.nameEN.also { professionType.nameEN = it }
        save(professionType)
    }
}

