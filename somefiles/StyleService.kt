package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.aspect.Locked
import com.musicbible.mapper.genre.CreateStyleInput
import com.musicbible.model.Style
import com.musicbible.repository.StyleRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface StyleService : TagService<Style>, StyleRepository {
    override val modelName: String
        get() = "风格"

    fun create(input: CreateStyleInput): Style
    fun update(style: Style, input: CreateStyleInput)
    fun sort(ids: List<UUID>): MutableList<Style>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class StyleServiceImpl(
    @Autowired val styleRepository: StyleRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : StyleService, StyleRepository by styleRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = styleRepository.countByIdInArtist(id) +
            styleRepository.countByIdInRecoding(id) +
            styleRepository.countByIdInRelease(id) +
            styleRepository.countByIdInWork(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<Style> {
        val list: MutableList<Style> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    override fun create(input: CreateStyleInput): Style {
        val style = Style(input.nameCN, input.nameEN)
        return save(style)
    }

    override fun create(nameCN: String, nameEN: String?): Style {
        val style = Style(nameCN, nameEN ?: "")
        return save(style)
    }

    @Locked("%{#style.id}")
    override fun update(style: Style, input: CreateStyleInput) {
        input.nameCN.also { style.nameCN = it }
        input.nameEN.also { style.nameEN = it }
        save(style)
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该风格与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }
}
