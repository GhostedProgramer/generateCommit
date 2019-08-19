package com.musicbible.service

import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.model.Artist
import com.musicbible.model.Saying
import com.musicbible.repository.SayingRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface SayingService : TimeKeepingService<Saying>, SayingRepository {
    override val modelName: String
        get() = "名言"

    fun create(artist: Artist?, images: Array<String>, contentCN: String, contentEN: String): Saying
    fun update(id: UUID, artistId: UUID?, images: Array<String>?, contentCN: String?, contentEN: String?)
    fun update(id: UUID, artist: Artist?, images: Array<String>?, contentCN: String?, contentEN: String?) {
        findOrThrow(id).also { saying ->
            artist?.also { saying.artist = it }
            images?.also { saying.images = it }
            contentCN?.also { saying.contentCN = it }
            contentEN?.also { saying.contentEN = it }
            save(saying)
        }
    }
}

@Service
@Transactional
class SayingServiceImpl(
    @Autowired val artistService: ArtistService,
    @Autowired val sayingRepository: SayingRepository
) : SayingService, SayingRepository by sayingRepository {

    @Locked("%{#id}")
    override fun update(id: UUID, artistId: UUID?, images: Array<String>?, contentCN: String?, contentEN: String?) {
        val artist = artistId?.let {
            artistService.findExistsOrThrow(artistId)
        }
        update(id, artist, images, contentCN, contentEN)
    }

    override fun create(artist: Artist?, images: Array<String>, contentCN: String, contentEN: String) =
        Saying().also {
            it.images = images
            it.contentCN = contentCN
            it.contentEN = contentEN
            it.artist = artist
            save(it)
        }
}
