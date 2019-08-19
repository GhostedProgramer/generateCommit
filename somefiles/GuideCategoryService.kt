package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.payload.PageQuery
import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.guide.GuideCategoryInput
import com.musicbible.mapper.guide.GuideCodeinput
import com.musicbible.mapper.guide.GuideInput
import com.musicbible.mapper.guide.GuideMapper
import com.musicbible.mapper.guide.GuideOutput
import com.musicbible.mapper.guide.GuideTitleInput
import com.musicbible.mapper.guide.MoveInput
import com.musicbible.model.GuideCategory
import com.musicbible.model.QGuideCategory
import com.musicbible.repository.GuideCategoryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface GuideCategoryService : TimeKeepingService<GuideCategory>, GuideCategoryRepository {
    override val modelName: String
        get() = "数据指南类别"

    fun createCategory(input: GuideCategoryInput): GuideCategory
    fun list(match: String?, pageQuery: PageQuery): Page<GuideCategory>
    fun quickUpdateModule(id: UUID, input: GuideTitleInput)
    fun quickUpdateCode(id: UUID, input: GuideCodeinput)
    fun createGuide(id: UUID, guideInput: List<GuideInput>): List<GuideOutput>
    fun moveGuide(moveInput: MoveInput)
}

@Service
@Transactional
class GuideCategoryImpl(
    val guideCategoryRepository: GuideCategoryRepository,
    @Autowired val guideMapper: GuideMapper
) : GuideCategoryService, GuideCategoryRepository by guideCategoryRepository {
    val qGuideCategory = QGuideCategory.guideCategory

    override fun list(match: String?, pageQuery: PageQuery): Page<GuideCategory> {
        val pageable = pageQuery.defaultSort("+order")
        return if (match != null) {
            findAll(qGuideCategory.module.contains(match), pageable)
        } else {
            findAll(pageable)
        }
    }

    override fun createCategory(input: GuideCategoryInput): GuideCategory {
        val maxOrder = (findAll().maxBy { it.order }?.order ?: -1) + 1
        val guideCategory = GuideCategory(input.module, maxOrder)
        if (existsByCode(input.code)) {
            throw AppError.BadRequest.default(msg = "编号已存在，请更换")
        }
        guideCategory.code = input.code
        return save(guideCategory)
    }

    @Locked("%{#id}")
    override fun quickUpdateModule(id: UUID, input: GuideTitleInput) {
        val category = findOrThrow(id)
        category.module = input.module
        save(category)
    }

    @Locked("%{#id}")
    override fun quickUpdateCode(id: UUID, input: GuideCodeinput) {
        val category = findOrThrow(id)
        if (existsByCode(input.code)) {
            throw AppError.BadRequest.default(msg = "编号已存在，请更换")
        }
        category.code = input.code
        save(category)
    }

    @Locked("%{#id}")
    override fun createGuide(id: UUID, guideInput: List<GuideInput>): List<GuideOutput> {
        val guideCategory = findOrThrow(id)
        guideCategory.guides.clear()
        guideCategory.guides.addAll(guideMapper.toListOfGuide(guideInput).map {
            it.guideCategory = guideCategory
            it
        })
        save(guideCategory)
        return guideCategory.guides
            .sortedBy { it.order }
            .let(guideMapper::toListOfGuideOutput)
    }

    @Locked("%{#moveInput.idUp}-%{#moveInput.idDown}")
    override fun moveGuide(moveInput: MoveInput) {
        val guideUp = findOrThrow(moveInput.idUp)
        val guideDown = findOrThrow(moveInput.idDown)

        val upOrder = guideDown.order
        val downOrder = guideUp.order
        guideUp.order = upOrder
        guideDown.order = downOrder
        saveAll(listOf(guideUp, guideDown))
    }

}
