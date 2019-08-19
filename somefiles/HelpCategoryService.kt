package com.musicbible.service

import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.help.HelpCategoryInput
import com.musicbible.mapper.help.HelpCategoryUpdateInput
import com.musicbible.mapper.help.MoveInput
import com.musicbible.model.HelpCategory
import com.musicbible.repository.HelpCategoryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID


interface HelpCategoryService : TimeKeepingService<HelpCategory>, HelpCategoryRepository {
    override val modelName: String
        get() = "帮助类别"

    fun createCategory(input: HelpCategoryInput): HelpCategory

    fun updateInfo(id: UUID, input: HelpCategoryUpdateInput)

    fun moveCategory(moveInput: MoveInput)

}

@Service
@Transactional
class HelpCategoryServiceImpl(
    @Autowired val helpCategoryRepository: HelpCategoryRepository
) : HelpCategoryService, HelpCategoryRepository by helpCategoryRepository {
    override fun createCategory(input: HelpCategoryInput): HelpCategory {
        val maxOrder = (findAll().maxBy { it.order }?.order ?: -1) + 1
        return save(HelpCategory(input.name, maxOrder).also { it.image = input.image })
    }

    @Locked("%{#id}")
    override fun updateInfo(id: UUID, input: HelpCategoryUpdateInput) {
        val help = findOrThrow(id)
        input.name?.also { help.name = it }
        input.image?.also { help.image = it }
    }

    @Locked("%{#moveInput.idUp}-%{#moveInput.idDown}")
    override fun moveCategory(moveInput: MoveInput) {
        val categoryUp = findOrThrow(moveInput.idUp)
        val categoryDown = findOrThrow(moveInput.idDown)

        val upOrder = categoryDown.order
        val downOrder = categoryUp.order
        categoryUp.order = upOrder
        categoryDown.order = downOrder
        saveAll(listOf(categoryUp, categoryDown))
    }
}
