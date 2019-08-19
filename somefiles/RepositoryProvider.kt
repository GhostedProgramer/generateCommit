package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.model.ModelEnum
import com.musicbible.repository.ArtistRepository
import com.musicbible.repository.CommentRepository
import com.musicbible.repository.LabelRepository
import com.musicbible.repository.RecordingRepository
import com.musicbible.repository.ReleaseRepository
import com.musicbible.repository.SaleRepository
import com.musicbible.repository.VideoRepository
import com.musicbible.repository.WorkRepository
import com.musicbible.repository.base.AppreciationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

interface RepositoryProvider<T> {
    fun get(type: ModelEnum): T
}

@Service
class RepositoryProviderImpl<T> : RepositoryProvider<T> {

    @Autowired
    lateinit var labelRepository: LabelRepository

    @Autowired
    lateinit var artistRepository: ArtistRepository

    @Autowired
    lateinit var workRepository: WorkRepository

    @Autowired
    lateinit var releaseRepository: ReleaseRepository

    @Autowired
    lateinit var saleRepository: SaleRepository

    @Autowired
    lateinit var videoRepository: VideoRepository

    @Autowired
    lateinit var commentRepository: CommentRepository

    @Autowired
    lateinit var appreciationRepository: AppreciationRepository

    @Autowired
    lateinit var recordingRepository: RecordingRepository

    @Suppress("UNCHECKED_CAST")
    override fun get(type: ModelEnum): T {
        return when (type) {
            ModelEnum.Label -> labelRepository
            ModelEnum.Artist -> artistRepository
            ModelEnum.Work -> workRepository
            ModelEnum.Release -> releaseRepository
            ModelEnum.Sale -> saleRepository
            ModelEnum.Video -> videoRepository
            ModelEnum.Comment -> commentRepository
            ModelEnum.Appreciation -> appreciationRepository
            ModelEnum.Recording -> recordingRepository
            else -> throw AppError.NotFound.default(msg = "未支持的类型: $type")
        } as T
    }
}

