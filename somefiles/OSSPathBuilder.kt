package com.musicbible.service

import com.boostfield.spring.constant.Profiles
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

interface OSSPathBuilder {
    enum class Type {
        agreement,
        appreciation,
        artist,
        artist_intro,
        avatar,
        banner,
        billboard,
        billboard_intro,
        data_feedback,
        feedback,
        guide,
        help,
        help_intro,
        label,
        label_intro,
        literature,
        media,
        release,
        release_intro,
        report,
        saying,
        video_intro,
        work_intro

    }

    fun build(type: Type, suffix: String): String
}

@Service
@Transactional
class OSSPathBuilderImpl(
    @Autowired val environment: Environment
) : OSSPathBuilder {
    @Suppress("MagicNumber")
    override fun build(type: OSSPathBuilder.Type, suffix: String): String {
        val env = if (environment.activeProfiles.contains(Profiles.PRODUCTION)) "production" else "dev"
        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.CHINA))
        return "$env/${type.name}/" + timestamp + "${Random().nextInt(900000) + 100000}" + ".$suffix"
    }
}
