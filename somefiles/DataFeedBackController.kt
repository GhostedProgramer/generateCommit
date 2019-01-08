package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.musicbible.mapper.dataFeedback.DataFeedbackBackendListInput
import com.musicbible.mapper.dataFeedback.DataFeedbackBackendListOutput
import com.musicbible.mapper.dataFeedback.DataFeedbackMapper
import com.musicbible.service.DataFeedbackService
import com.musicbible.service.EntityMapService
import com.musicbible.service.RepositoryProvider
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/feedback/data")
@Api(value = "/api/v0/feedback/data", tags = ["S 数据报错"], description = "dataFeedback")
class DataFeedBackController(
    @Autowired val dataFeedbackService: DataFeedbackService,
    @Autowired val dataFeedbackMapper: DataFeedbackMapper,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired val entityMapService: EntityMapService
) : BaseController() {

    @ApiOperation(value = "列表",
        notes = """
            subject:
            release:id, titleCN, title1, images, catalogs, deleted
            artist: id, lastName, nameCN, firstName, abbrCN, abbr, images, deleted
            work: id, title, titleCN, catalogNumbers, deleted
            label: id, name, nameCN, images
        """
    )
    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_EXCEPTION')")
    fun backendList(@Valid input: DataFeedbackBackendListInput): PageResponse<DataFeedbackBackendListOutput> {
        val output = dataFeedbackService.getBackendList(input)
            .map(dataFeedbackMapper::toDataFeedbackBackendList)
        output.forEach {
            val softDeletable = targetRepositoryProvider.get(it.targetType).findById(it.targetId).get()
            logger.debug("实体类型为$softDeletable")
            it.subject = softDeletable.let { target -> entityMapService.toIdentity(target) }
        }
        return output.let(RestResponse::page)
    }

    @ApiOperation("忽略")
    @PreAuthorize("hasAuthority('MANAGE_EXCEPTION')")
    @PutMapping("/{id}/ignore")
    fun ignore(@PathVariable id: UUID) {
        val dataFeedback = dataFeedbackService.findOrThrow(id)
        dataFeedbackService.ignore(dataFeedback)
    }

    @ApiOperation("修改")
    @PreAuthorize("hasAuthority('MANAGE_EXCEPTION')")
    @PutMapping("/{id}/modify")
    fun modify(@PathVariable id: UUID) {
        val dataFeedback = dataFeedbackService.findOrThrow(id)
        dataFeedbackService.modify(dataFeedback)
    }
}
