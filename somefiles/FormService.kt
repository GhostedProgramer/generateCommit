package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.aspect.Locked
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Form
import com.musicbible.repository.FormRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface FormService : FormRepository, TagService<Form> {
    override val modelName: String
        get() = "体裁"

    fun update(form: Form, input: CreateNameInput)
    fun sort(ids: List<UUID>): MutableList<Form>
    fun searchRaletedness(id: UUID): Boolean
    fun deleteAndCheck(id: UUID)
}

@Service
@Transactional
class FormServiceImpl(
    @Autowired val formRepository: FormRepository,
    @Autowired val categoryCacheService: CategoryCacheService
) : FormService, FormRepository by formRepository {
    override fun searchRaletedness(id: UUID): Boolean {
        val result = formRepository.countByIdInRelease(id) +
            formRepository.countByIdInWork(id)
        return result != 0L
    }

    override fun sort(ids: List<UUID>): MutableList<Form> {
        val list: MutableList<Form> = mutableListOf()
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
            throw AppError.Forbidden.default(msg = "该体裁与其他实体有关联,无法删除")
        } else {
            findAndDelete(id)
            categoryCacheService.refresh()
        }
    }

    @Locked("%{#form.id}")
    override fun update(form: Form, input: CreateNameInput) {
        input.nameCN.also { form.nameCN = it }
        input.nameEN.also { form.nameEN = it }
        save(form)
    }

    override fun create(nameCN: String, nameEN: String?): Form {
        val form = Form(nameCN, nameEN ?: "")
        return save(form)
    }
}
