package com.musicbible.service

import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.model.Guide
import com.musicbible.repository.GuideRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface GuideService : TimeKeepingService<Guide>, GuideRepository {
    override val modelName: String
        get() = "数据指南"
}

@Service
@Transactional
class GuideImpl(
    @Autowired val guideRepository: GuideRepository
) : GuideService, GuideRepository by guideRepository
