package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.BaseService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.profession.CreateProfessionInput
import com.musicbible.model.Profession
import com.musicbible.model.ProfessionType
import com.musicbible.repository.ProfessionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ProfessionService : ProfessionRepository, BaseService<Profession> {
    override val modelName: String
        get() = "职业"

    fun add(req: CreateProfessionInput): Profession
    fun create(type: ProfessionType, nameCN: String, nameEN: String?): Profession
    fun update(profession: Profession, input: CreateProfessionInput)
    fun sort(ids: List<UUID>): MutableList<Profession>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class ProfessionServiceImpl(
    @Autowired val professionTypeService: ProfessionTypeService,
    @Autowired val professionRepository: ProfessionRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : ProfessionService, ProfessionRepository by professionRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = professionRepository.countByIdInWork(id) +
            professionRepository.countByIdInRecording(id) +
            professionRepository.countByIdInArtist(id)
        return result != 0L
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该艺术家职业与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun sort(ids: List<UUID>): MutableList<Profession> {
        val list: MutableList<Profession> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#profession.id}")
    override fun update(profession: Profession, input: CreateProfessionInput) {
        input.typeId.also { profession.type = professionTypeService.findOrThrow(it) }
        input.nameCN.also { profession.nameCN = it }
        input.nameEN.also { profession.nameEN = it }
        save(profession)
    }

    override fun add(req: CreateProfessionInput) =
        save(Profession(professionTypeService.findOrThrow(req.typeId), req.nameCN, req.nameEN))

    override fun create(type: ProfessionType, nameCN: String, nameEN: String?): Profession {
        val profession = Profession(type, nameCN, nameEN ?: "")
        return save(profession)
    }
}


