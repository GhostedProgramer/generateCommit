package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.payload.PageQuery
import com.musicbible.mapper.appversion.AppVersionInput
import com.musicbible.mapper.appversion.AppVersionMapper
import com.musicbible.mapper.appversion.AppVersionOutput
import com.musicbible.mapper.appversion.AppVersionSettingInput
import com.musicbible.mapper.appversion.LastSupportOutput
import com.musicbible.model.AppVersionSetting
import com.musicbible.model.ClientType
import com.musicbible.service.AppVersionService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
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
@RequestMapping("/api/v0/appVersion")
@Api(value = "/api/v0/appVersion", tags = ["App版本"], description = "AppVersion")
class AppVersionController(
    @Autowired val appVersionService: AppVersionService,
    @Autowired val appVersionMapper: AppVersionMapper
) {
    @ApiOperation("新建App版本")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @PostMapping
    fun addAppVersion(@Valid @RequestBody input: AppVersionInput): CreatedResponse {
        return RestResponse.created(appVersionService.create(input.clientType, input.version, input.changeLog, input.downloadUrl))
    }

    @ApiOperation("带条件分页查询版本记录")
    @ApiImplicitParams(
        ApiImplicitParam(name = "clientType", required = false)
    )
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @GetMapping
    fun listAppVersionsForPage(
        @Valid @RequestParam clientType: ClientType?,
        @Valid pageQuery: PageQuery
    ): PageResponse<AppVersionOutput> {
        return appVersionService.page(clientType, pageQuery.pageable())
            .map(appVersionMapper::appVersionToAppVersionOutput)
            .let(RestResponse::page)
    }

    @ApiOperation("查询所有版本记录")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @GetMapping("/all")
    fun listAll(): List<AppVersionOutput> {
        return appVersionService.findExists()
            .map(appVersionMapper::appVersionToAppVersionOutput)
    }

    @ApiOperation("查询版本记录")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @GetMapping("/{id}")
    fun getAppVersionById(@PathVariable id: UUID): AppVersionOutput {
        return appVersionService
            .findExistsOrThrow(id)
            .let { appVersionMapper.appVersionToAppVersionOutput(it) }
    }

    @ApiOperation("删除版本记录")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @DeleteMapping("/{id}")
    fun deleteAppVersion(@PathVariable id: UUID) {
        appVersionService.delete(id)
    }

    @ApiOperation("更新版本记录")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @PutMapping("/{id}")
    fun updateAppVersion(
        @PathVariable(required = true) id: UUID,
        @Valid @RequestBody req: AppVersionInput
    ) {
        appVersionService.update(id, req)
    }

    @ApiOperation("将某个版本设为最后一个受支持的版本")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @PutMapping("/{id}/last/support")
    fun setLastSupportVersion(@PathVariable id: UUID, @Valid @RequestBody appVersionSettingInput: AppVersionSettingInput) {
        val appVersion = appVersionService.findExistsOrThrow(id)
        appVersionService.setAppVersionSetting(appVersionSettingInput.appVersionSetting, appVersion)
    }

    @ApiOperation("删除最后一个受支持的版本")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @PutMapping("/last/support")
    fun deleteLastSupportVersion(@Valid @RequestBody appVersionSettingInput: AppVersionSettingInput) {
        appVersionService.clearLastSupport(appVersionSettingInput.appVersionSetting)
    }

    @ApiOperation("获取最后一个受支持的版本")
    @PreAuthorize("hasAuthority('MANAGE_APP')")
    @GetMapping("/last/support")
    fun getLastSupportVersion(@Valid @RequestParam appVersionSetting: AppVersionSetting): LastSupportOutput =
        appVersionMapper.toLastVersionOutput(appVersionService.getAppVersionSetting(appVersionSetting))
}
