package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.service.BaseService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.country.CreateCountryInput
import com.musicbible.model.Country
import com.musicbible.repository.CountryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface CountryService : BaseService<Country>, CountryRepository {
    override val modelName: String
        get() = "国家"

    fun saveIfNotExists(code: String, nameEN: String, nameCN: String): Country
    fun create(input: CreateCountryInput): Country
    fun update(country: Country, input: CreateCountryInput)
    fun sort(ids: List<UUID>): MutableList<Country>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class CountryServiceImpl(
    @Autowired val countryRepository: CountryRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : CountryService, CountryRepository by countryRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = countryRepository.countByIdInRelease(id) +
            countryRepository.countByIdInArtist(id) +
            countryRepository.countByIdInLabel(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<Country> {
        val list: MutableList<Country> = mutableListOf()
        var number: Int = ids.size
        ids.forEach {
            val ob = findOrThrow(it)
            ob.weight = number
            number--
            list.add(save(ob))
        }
        return list
    }

    @Locked("%{#country.id}")
    override fun update(country: Country, input: CreateCountryInput) {
        input.code.also { country.code = it }
        input.nameCN.also { country.nameCN = it }
        input.nameEN.also { country.nameEN = it }
        save(country)
    }

    @Locked("%{#id}")
    override fun deleteAndCheck(id: UUID) {
        if (searchRaletedness(id)) {
            throw AppError.Forbidden.default(msg = "该国家与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    override fun saveIfNotExists(code: String, nameEN: String, nameCN: String): Country {
        return findByCode(code) ?: countryRepository.save(Country(code, nameEN, nameCN))
    }

    override fun create(input: CreateCountryInput): Country {
        val country = Country(input.code, input.nameEN, input.nameCN)
        return save(country)
    }
}
