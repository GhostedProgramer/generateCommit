package com.musicbible.service

import com.boostfield.spring.service.CompletionSuggestResult
import com.boostfield.spring.service.CompletionSuggestService
import com.boostfield.spring.service.KeyOption
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


interface SearchSuggestService {
    fun suggest(word: String): List<CompletionSuggestResult>
}

@Service
class SearchSuggestServiceImpl(
    @Autowired val releaseService: ReleaseService,
    @Autowired val artistService: ArtistService,
    @Autowired val workService: WorkService,
    @Autowired val labelService: LabelService,
    @Autowired val recordingService: RecordingService,
    @Autowired val videoService: VideoService,
    @Autowired val appreciationService: AppreciationService
) : SearchSuggestService {
    override fun suggest(word: String): List<CompletionSuggestResult> {
        val suggestServices = listOf<CompletionSuggestService>(
            releaseService,
            artistService,
            workService,
            labelService,
            videoService,
            appreciationService
        )

        val raw = suggestServices.map { GlobalScope.async { it.completionSuggest(word) } }
            .map { runBlocking { it.await() } }
            .toMutableList()

        raw.add(CompletionSuggestResult("all",
            raw.map {
                it.keyOptions.map { it.options }.flatten()
            }.flatten().distinct().let {
                listOf(KeyOption("all", it))
            }
        ))
        return raw
    }
}
