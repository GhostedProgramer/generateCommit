package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.help.HelpCategoryInput
import com.musicbible.mapper.help.HelpCategoryOutput
import com.musicbible.mapper.help.HelpCategoryUpdateInput
import com.musicbible.mapper.help.HelpInput
import com.musicbible.mapper.help.HelpMapper
import com.musicbible.mapper.help.HelpOutput
import com.musicbible.mapper.help.MoveInput
import com.musicbible.service.HelpCategoryService
import com.musicbible.service.HelpService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid


@RestController
@RequestMapping("/api/v0/helpCategory")
@Api(value = "/api/v0/helpCategory", tags = ["B 帮助"], description = "Help")
class HelpController(
    @Autowired val helpService: HelpService,
    @Autowired val helpCategoryService: HelpCategoryService,
    @Autowired val helpMapper: HelpMapper
) : BaseController() {

    @ApiOperation("新建问题分类")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @PostMapping
    fun createCategory(@Valid @RequestBody input: HelpCategoryInput): CreatedResponse {
        val item = helpCategoryService.createCategory(input)
        return RestResponse.created(item.id)
    }

    @ApiOperation("删除问题分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    fun deleteCategory(@PathVariable id: UUID) {
        helpCategoryService.findOrThrow(id)
            .also {
                helpCategoryService.delete(it)
            }
    }

    @ApiOperation("删除问题")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @DeleteMapping("/help/{id}")
    fun deleteHelp(@PathVariable id: UUID) {
        helpService.findOrThrow(id)
            .also {
                it.helpCategory = null
                helpService.delete(it)
            }
    }

    @ApiOperation("新建/编辑问题")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @PostMapping("/{id}/help")
    fun createQuestion(@PathVariable id: UUID, @Valid @RequestBody inputs: List<HelpInput>): List<HelpOutput> {
        var category = helpCategoryService.findOrThrow(id)
        category.helps.clear()
        category.helps.addAll(helpMapper.toListOfHelp(inputs).map {
            it.helpCategory = category
            it
        })
        category = helpCategoryService.save(category)
        return category.helps
            .let(helpMapper::toListOfHelpOutput)
    }

    @ApiOperation("分类列表")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @GetMapping
    fun helpCategoryList(
        @Valid @Validated pageQuery: PageQuery, @RequestParam(required = false) match: String?
    ): List<HelpCategoryOutput> {
        return if (match.isNullOrEmpty()) {
            helpCategoryService.findAll().sortedBy { it.order }
        } else {
            helpCategoryService.findByNameContaining(match).sortedBy { it.order }
        }.let {
            helpMapper.toListOfHelpCategoryOutput(it)
        }
    }

    @ApiOperation("问题列表")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @GetMapping("/{id}/help")
    fun helpList(@PathVariable id: UUID): List<HelpOutput> {
        return helpCategoryService.findOrThrow(id)
            .let {
                helpMapper.toListOfHelpOutput(it.helps)
            }
    }

    @ApiOperation("分类修改{上下移}")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @PutMapping("/move")
    fun moveCategory(@Valid @RequestBody moveInput: MoveInput) {
        helpCategoryService.moveCategory(moveInput)
    }

    @ApiOperation("分类编辑")
    @PreAuthorize("hasAuthority('MANAGE_HELP')")
    @PutMapping("/{id}")
    fun updateCategory(@PathVariable id: UUID, @Valid @RequestBody input: HelpCategoryUpdateInput) {
        helpCategoryService.updateInfo(id, input)
    }
}
