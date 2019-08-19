package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.BaseService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.instrument.CreateInstrumentInput
import com.musicbible.mapper.instrument.UpdateInstrumentInput
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Instrument
import com.musicbible.model.InstrumentType
import com.musicbible.repository.InstrumentRepository
import com.musicbible.repository.InstrumentTypeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface InstrumentService : InstrumentRepository, BaseService<Instrument> {
    override val modelName: String
        get() = "乐器"

    fun add(req: CreateInstrumentInput): Instrument
    fun create(type: InstrumentType, nameCN: String, nameEN: String?): Instrument
    fun update(instrument: Instrument, input: UpdateInstrumentInput)
    fun sort(ids: List<UUID>): MutableList<Instrument>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

interface InstrumentTypeService : InstrumentTypeRepository, TagService<InstrumentType> {
    override val modelName: String
        get() = "乐器类型"

    fun updateType(instrumentType: InstrumentType, input: CreateNameInput)
    fun sort(ids: List<UUID>): MutableList<InstrumentType>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class InstrumentServiceImpl(
    @Autowired val instrumentRepository: InstrumentRepository,
    @Autowired val instrumentTypeService: InstrumentTypeService,
    @Autowired val categoryCacheService: CategoryCacheService
) : InstrumentService, InstrumentRepository by instrumentRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = instrumentRepository.countByIdInRelease(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<Instrument> {
        val list: MutableList<Instrument> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该乐器与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    @Locked("%{#instrument.id}")
    override fun update(instrument: Instrument, input: UpdateInstrumentInput) {
        input.typeId.also { instrument.type = instrumentTypeService.findOrThrow(it) }
        input.nameCN.also { instrument.nameCN = it }
        input.nameEN.also { instrument.nameEN = it }
        save(instrument)
    }

    override fun add(req: CreateInstrumentInput): Instrument {
        val instrumentType = instrumentTypeService.findOrThrow(req.type)
        return save(Instrument(nameCN = req.nameCN, nameEN = req.nameEN, type = instrumentType))
    }

    override fun create(type: InstrumentType, nameCN: String, nameEN: String?): Instrument {
        val instrument = Instrument(type, nameCN, nameEN ?: "")
        return save(instrument)
    }
}

@Service
class InstrumentTypeServiceImpl(
    @Autowired val instrumentTypeRepository: InstrumentTypeRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : InstrumentTypeService, InstrumentTypeRepository by instrumentTypeRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = instrumentTypeRepository.countByIdInInstrument(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<InstrumentType> {
        val list: MutableList<InstrumentType> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#instrumentType.id}")
    override fun updateType(instrumentType: InstrumentType, input: CreateNameInput) {
        input.nameCN.also { instrumentType.nameCN = it }
        input.nameEN.also { instrumentType.nameEN = it }
        save(instrumentType)
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该乐器类型与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun create(nameCN: String, nameEN: String?): InstrumentType {
        val instrumentType = InstrumentType(nameCN, nameEN ?: "")
        return save(instrumentType)
    }
}
