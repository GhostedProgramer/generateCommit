package com.musicbible.service

import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.model.Help
import com.musicbible.repository.HelpRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface HelpService : TimeKeepingService<Help>, HelpRepository {
    override val modelName: String
        get() = "帮助"
}

@Service
@Transactional
class HelpServiceImpl(
    @Autowired val helpRepository: HelpRepository
) : HelpService, HelpRepository by helpRepository
