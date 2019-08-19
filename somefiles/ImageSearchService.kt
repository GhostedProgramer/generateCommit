package com.musicbible.service

import com.boostfield.baidu.imagesearch.BaiduImageSearchService
import com.boostfield.baidu.imagesearch.ErrorCode
import com.boostfield.baidu.imagesearch.ImageSearchException
import com.boostfield.baidu.imagesearch.SearchResult
import com.boostfield.spring.exception.AppError
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

const val IMAGE_STYLE = "style/search-index"
const val SEARCH_IMAGE_MAX_RESULT = 50

interface ImageSearchService {
    fun similarDelete(path: String)

    fun similarIndexRelease(releaseId: UUID, path: String)

    fun similarSearch(image: ByteArray): List<SearchResult>
}

@Service
class ImageSearchServiceImpl(
    @Value("\${app.image.base-url}") val imageBaseUrl: String,
    @Autowired val baiduImageSearchService: BaiduImageSearchService
) : ImageSearchService {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun similarDelete(path: String) {
        val url = URI("$imageBaseUrl/$path?x-oss-process=$IMAGE_STYLE")
            .normalize().toString()
        baiduImageSearchService.similarDelete(url)
    }

    override fun similarIndexRelease(releaseId: UUID, path: String) {
        val brief = mapOf(
            "releaseId" to releaseId,
            "imagePath" to path
        )
        val url = URI("$imageBaseUrl/$path?x-oss-process=$IMAGE_STYLE")
            .normalize().toString()
        try {
            baiduImageSearchService.similarIndex(url, brief)
        } catch (ex: ImageSearchException) {
            logger.warn("Index image[{}] failed: {}:{}", path, ex.code, ex.message)
            processException(ex)
        }
    }

    override fun similarSearch(image: ByteArray): List<SearchResult> {
        return try {
            baiduImageSearchService.similarSearch(image, skip = 0, limit = SEARCH_IMAGE_MAX_RESULT)
        } catch (ex: ImageSearchException) {
            logger.warn("Search image failed: {}:{}", ex.code, ex.message)
            processException(ex)
            return listOf()
        }
    }

    private fun processException(ex: ImageSearchException) {
        when (ex.code) {
            ErrorCode.ITEM_EXIST -> Unit
            ErrorCode.IMAGE_FORMAT_ERROR,
            ErrorCode.IMAGE_SIZE_ERROR,
            ErrorCode.INPUT_IMAGE_CANNOT_HANDLE,
            ErrorCode.RECOGNIZE_ERROR,
            ErrorCode.URL_FORMAT_ILLEGAL,
            ErrorCode.IMAGE_RECOGNIZE_ERROR,
            ErrorCode.SDK_UNSUPPORTED_IMAGE_FORMAT,
            ErrorCode.SDK_IMAGE_SIZE_ERROR,
            ErrorCode.SDK_IMAGE_LENGTH_ERROR ->
                throw AppError.BadRequest.paramError(ErrorCode.getMessage(ex.code))
            else ->
                throw AppError.Internal.default(msg = ErrorCode.getMessage(ex.code))
        }
    }
}
