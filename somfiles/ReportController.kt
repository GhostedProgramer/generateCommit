package com.musicbible.controller

import com.boostfield.spring.exception.AppError
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.musicbible.mapper.report.ReportBackendListInput
import com.musicbible.mapper.report.ReportBackendListOutput
import com.musicbible.mapper.report.ReportMapper
import com.musicbible.model.Appreciation
import com.musicbible.model.Comment
import com.musicbible.model.Sale
import com.musicbible.model.Video
import com.musicbible.service.AppreciationService
import com.musicbible.service.CommentService
import com.musicbible.service.ReportService
import com.musicbible.service.RepositoryProvider
import com.musicbible.service.SaleService
import com.musicbible.service.VideoService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/report")
@Api("/api/v0/report", tags = ["J 举报"], description = "Report")
class ReportController(
    @Autowired val reportMapper: ReportMapper,
    @Autowired val reportService: ReportService,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired val videoService: VideoService,
    @Autowired val appreciationService: AppreciationService,
    @Autowired val saleService: SaleService,
    @Autowired val commentService: CommentService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) {

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('MANAGE_REPORT')")
    @ApiImplicitParam(
        name = "key", allowableValues = "ABUSE,AD,NONSENSE,VIOLENCE,SWINDLE,REACTION,OTHER",
        value = "ABUSE: 恶意谩骂攻击,AD: 营销广告,NONSENSE: 无意义的言论,VIOLENCE: 色情/暴力,SWINDLE: 诈骗,REACTION: 反动,OTHER: 其他"
    )
    @GetMapping
    fun list(@Valid input: ReportBackendListInput): PageResponse<ReportBackendListOutput> {
        return reportService.findList(input)
            .map(reportMapper::toBackendReportList)
            .let(RestResponse::page)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_REPORT')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        val report = reportService.findOrThrow(id)
        val target = targetRepositoryProvider
            .get(report.targetType).findByDeletedFalseAndId(report.targetId)
        when (target) {
            is Video -> {
                videoService.softDelete(target)
                videoService.afterPublishedBeDelete(target)
            }
            is Appreciation -> {
                appreciationService.softDelete(target)
                appreciationService.afterPublishedBeDelete(target)
            }
            is Sale -> saleService.softDelete(target)
            is Comment -> commentService.softDelete(target)
            else -> throw AppError.NotFound.default(msg = "$target 该实体无法通过举报列表被删除")
        }
        reportService.remove(report)
    }

    @ApiOperation("忽略")
    @PreAuthorize("hasAuthority('MANAGE_REPORT')")
    @PutMapping("/{id}/status")
    fun ignore(@PathVariable id: UUID) {
        val report = reportService.findOrThrow(id)
        reportService.ignore(report)
    }
}
