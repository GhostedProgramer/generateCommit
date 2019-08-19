package com.musicbible.load

import com.musicbible.service.SayingService
import org.springframework.beans.factory.annotation.Autowired

import org.springframework.stereotype.Component

@Component
class SayingLoader(
    @Autowired val sayingService: SayingService
) : Loader {

    override fun load() {
        sayingService.create(null, emptyArray(), "占位1", "")
        sayingService.create(null, emptyArray(), "占位2", "")
    }

    override fun isLoad(): Boolean {
        return sayingService.count() == 2L
    }
}
