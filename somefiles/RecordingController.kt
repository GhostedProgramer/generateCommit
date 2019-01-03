package com.musicbible.controller

import com.musicbible.mapper.audio.AudioDetailOutput
import com.musicbible.mapper.audio.AudioMapper
import com.musicbible.mapper.audio.UpdateAudioInput
import com.musicbible.mapper.recording.*
import com.musicbible.service.RecordingService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/recording")
@Api(value = "/api/v0/recording", tags = ["L 录音"], description = "Recording")
class RecordingController(
    @Autowired val recordingService: RecordingService,
    @Autowired val recordingMapper: RecordingMapper,
    @Autowired val audioMapper: AudioMapper
) {

    @ApiOperation("详情")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): RecordingBackendDetailOutput {
        val recording = recordingService.findExistsOrThrow(id)
        return recordingMapper.toBackendDetail(recording)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        val recording = recordingService.findUnarchivedOrThrow(id)
        recordingService.softDelete(recording)
    }

    @ApiOperation("修改艺术家")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}/artists")
    fun updateArtists(@PathVariable id: UUID, @Valid @RequestBody body: List<UpdateRecordingArtistInput>) {
        recordingService.updateArtistsAndSyncReleaseArtist(id, body)
    }

    @ApiOperation("修改基本字段")
    @PreAuthorize("hasAuthority('UPDATE_RELEASE')")
    @PutMapping("/{id}")
    fun updateFields(@PathVariable id: UUID, @Valid @RequestBody body: UpdateRecordingInput) {
        val recording = recordingService.findUnarchivedOrThrow(id)
        recordingService.updateFields(recording, body)
    }

    @ApiOperation("修改音频")
    @PutMapping("/{id}/audio")
    fun updateAudio(
        @PathVariable id: UUID, @Valid @RequestBody body: UpdateAudioInput
    ): AudioDetailOutput {
        val audio = recordingService.updateAudio(id, body)
        return audioMapper.toDetail(audio)
    }

    @ApiOperation("删除音频")
    @DeleteMapping("/{id}/audio")
    fun deleteAudio(@PathVariable id: UUID) {
        recordingService.deleteAudio(id)
    }
}
