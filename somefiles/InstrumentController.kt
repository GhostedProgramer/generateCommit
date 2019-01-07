package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.instrument.CreateInstrumentInput
import com.musicbible.mapper.instrument.InstrumentMapper
import com.musicbible.mapper.instrument.InstrumentTypeOutput
import com.musicbible.mapper.instrument.UpdateInstrumentInput
import com.musicbible.mapper.media.CreateNameInput
import com.musicbible.model.Instrument
import com.musicbible.model.InstrumentType
import com.musicbible.service.CategoryCacheService
import com.musicbible.service.InstrumentService
import com.musicbible.service.InstrumentTypeService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/instrument")
@Api(value = "/api/v0/instrument", tags = ["Y 乐器"], description = "Instrument")
class InstrumentController(
    @Autowired val instrumentService: InstrumentService,
    @Autowired val instrumentTypeService: InstrumentTypeService,
    @Autowired val instrumentMapper: InstrumentMapper,
    @Autowired val categoryCacheService: CategoryCacheService
) {
    @ApiOperation("新建乐器")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping
    fun addInstrument(@Valid @RequestBody req: CreateInstrumentInput): CreatedResponse {
        val instrument = instrumentService.add(req)
        categoryCacheService.refresh()
        return RestResponse.created(instrument)
    }

    @ApiOperation("新建乐器类型")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PostMapping("/type")
    fun addInstrumentType(@Valid @RequestBody req: CreateNameInput): CreatedResponse {
        val instrumentType = instrumentTypeService.save(InstrumentType(req.nameCN, req.nameEN))
        categoryCacheService.refresh()
        return RestResponse.created(instrumentType)
    }

    @ApiOperation("删除乐器")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/{id}")
    fun removeInstrument(@PathVariable id: UUID) {
        instrumentService.deleteAndCheck(id)
    }

    @ApiOperation("删除乐器类型")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @DeleteMapping("/type/{id}")
    fun removeInstrumentType(@PathVariable id: UUID) {
        instrumentTypeService.deleteAndCheck(id)
    }

    @ApiOperation("修改乐器分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/{id}")
    fun updateInstrument(@PathVariable id: UUID, @Valid @RequestBody input: UpdateInstrumentInput) {
        val instrument = instrumentService.findOrThrow(id)
        categoryCacheService.refresh()
        instrumentService.update(instrument, input)
    }

    @ApiOperation("修改乐器类型分类信息")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/type/{id}")
    fun updateInstrumentType(@PathVariable id: UUID, @Valid @RequestBody input: CreateNameInput) {
        val instrumentType = instrumentTypeService.findOrThrow(id)
        categoryCacheService.refresh()
        instrumentTypeService.updateType(instrumentType, input)
    }


    @ApiOperation("获取所有乐器和类型")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @GetMapping
    @Transactional
    fun getInstruments(): List<InstrumentTypeOutput> = instrumentTypeService
        .findAll(Sort.by(Sort.Direction.DESC, "weight"))
        .map(instrumentMapper::toInstrumentTypeOutput)

    @ApiOperation("更改乐器排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping
    fun changeLocation(@RequestBody ids: List<UUID>): MutableList<Instrument> {
        categoryCacheService.refresh()
        return instrumentService.sort(ids)
    }

    @ApiOperation("更改乐器类型排序")
    @PreAuthorize("hasAuthority('MANAGE_CATEGORY')")
    @PutMapping("/type")
    fun typeChangeLocation(@RequestBody ids: List<UUID>): MutableList<InstrumentType> {
        categoryCacheService.refresh()
        return instrumentTypeService.sort(ids)
    }
}
