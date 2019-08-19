package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.exception.AppError
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.persistence.SoftDeletable
import com.boostfield.spring.persistence.SoftDeletableRepository
import com.musicbible.mapper.appreciation.AppreciationBackendListInput
import com.musicbible.mapper.appreciation.AppreciationMapper
import com.musicbible.mapper.appreciation.BackendAppreciationListingOutput
import com.musicbible.mapper.appreciation.UpdateCommendLevelInput
import com.musicbible.model.User
import com.musicbible.security.UserOrThrow
import com.musicbible.service.AppreciationService
import com.musicbible.service.EntityMapService
import com.musicbible.service.RepositoryProvider
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/appreciation")
@Api("/api/v0/appreciation", tags = ["C 长评"], description = "Appreciation")
class AppreciationController(
    @Autowired val appreciationService: AppreciationService,
    @Autowired val appreciationMapper: AppreciationMapper,
    @Autowired val targetRepositoryProvider: RepositoryProvider<SoftDeletableRepository<SoftDeletable>>,
    @Autowired val entityMapService: EntityMapService
) : BaseController() {

    @ApiOperation(
        value = "后台长评列表",
        notes = "subject: \n" +
            "\t release: id, titleCN, title1, images, deleted\n" +
            "\t artist: id, lastName, nameCN, firstName, abbrCN, abbr, images, deleted\n" +
            "\t work: id, title, titleCN, deleted\n" +
            "\t sale: id, title1, titleCN, deleted\n" +
            "\t video: id, name, deleted\n" +
            "\t appreciation: id, title, targetType, targetId, deleted\n"
    )
    @PreAuthorize("hasAuthority('READ_APPRECIATION')")
    @ApiImplicitParam(name = "sort", value = "createdAt:创建时间,commendLevel:推荐等级", allowableValues = "createdAt,commendLevel")
    @GetMapping
    fun appreciationList(@Valid input: AppreciationBackendListInput): PageResponse<BackendAppreciationListingOutput> {
        val output = appreciationService.listBackend(input)
            .map(appreciationMapper::toBackendAppreciationListingOutput)
        output.forEach {
            val target = targetRepositoryProvider
                .get(it.targetType!!)
                .findById(it.targetId!!)
                .orElseThrow { AppError.NotFound.default(msg = "需要的数据已从数据库彻底删除,请联系管理员处理") }
            it.subject = entityMapService.toIdentity(target)
        }

        return RestResponse.page(output)
    }

    @ApiOperation(
        value = "删除用户创建的长评",
        notes = """
            1. 长评从属于用户
            2. 删除成功后发送通知
        """)
    @PreAuthorize("hasAuthority('DELETE_APPRECIATION')")
    @DeleteMapping("/{id}/created_by_user")
    fun deleteAppreciationCreatedByUser(@PathVariable id: UUID, @RequestParam reason: String, @UserOrThrow user: User) {
        appreciationService.deleteUserCreated(user, id, reason)
    }

    @ApiOperation("修改权重")
    @PreAuthorize("hasAuthority('READ_APPRECIATION')")
    @PutMapping("/{id}/commendLevel")
    fun updateCommendLevel(@PathVariable id: UUID, @Valid @RequestBody body: UpdateCommendLevelInput) {
        appreciationService.updateCommendLevel(id, body.commendLevel)
    }
}
