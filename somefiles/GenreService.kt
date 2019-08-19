package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.aspect.Locked
import com.musicbible.mapper.genre.CreateGenreInput
import com.musicbible.model.Genre
import com.musicbible.repository.GenreRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface GenreService : TagService<Genre>, GenreRepository {
    override val modelName: String
        get() = "流派"

    fun create(input: CreateGenreInput): Genre
    fun update(genre: Genre, input: CreateGenreInput)
    fun sort(ids: List<UUID>): MutableList<Genre>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class GenreServiceImpl(
    @Autowired val genreRepository: GenreRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : GenreService, GenreRepository by genreRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = genreRepository.countByGenreIdInArtist(id) +
            genreRepository.countByGenreIdInRecoding(id) +
            genreRepository.countByGenreIdInRelease(id) +
            genreRepository.countByGenreIdInWork(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<Genre> {
        val list: MutableList<Genre> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#genre.id}")
    override fun update(genre: Genre, input: CreateGenreInput) {
        input.nameCN.also { genre.nameCN = it }
        input.nameEN.also { genre.nameEN = it }
        save(genre)
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该流派与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun create(input: CreateGenreInput): Genre {
        val genre = Genre(input.nameCN, input.nameEN)
        return save(genre)
    }

    override fun create(nameCN: String, nameEN: String?): Genre {
        val genre = Genre(nameCN, nameEN ?: "")
        return save(genre)
    }
}
