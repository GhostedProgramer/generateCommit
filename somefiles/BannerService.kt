package com.musicbible.service

import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.boostfield.spring.service.TimeKeepingService
import com.musicbible.aspect.Locked
import com.musicbible.mapper.banner.BackendBannerListingOutput
import com.musicbible.mapper.banner.BannerBackendListInput
import com.musicbible.mapper.banner.BannerMapper
import com.musicbible.mapper.banner.UpdateBannerImageInput
import com.musicbible.mapper.banner.UpdateBannerInput
import com.musicbible.model.Banner
import com.musicbible.model.QBanner
import com.musicbible.repository.BannerRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface BannerService :
    TimeKeepingService<Banner>, BannerRepository {
    override val modelName: String
        get() = "轮播图"

    fun updateWebImages(banner: Banner, body: UpdateBannerImageInput)
    fun updateFields(banner: Banner, fields: UpdateBannerInput)
    fun findList(input: BannerBackendListInput): PageResponse<BackendBannerListingOutput>
    fun delete(id: UUID)
}


@Service
@Transactional
class BannerServiceImp(
    @Autowired val bannerRepository: BannerRepository,
    @Autowired val bannerMapper: BannerMapper
) : BannerService, BannerRepository by bannerRepository {
    val qBanner: QBanner = QBanner.banner

    override fun findList(input: BannerBackendListInput): PageResponse<BackendBannerListingOutput> {
        return if (input.q == null) {
            findAll(input.pageable())
                .map(bannerMapper::toBackendBannerListingOutput)
                .let<Page<BackendBannerListingOutput>, PageResponse<BackendBannerListingOutput>>(RestResponse::page)
        } else {
            findAll(qBanner.name.contains("${input.q}"), input.pageable())
                .map(bannerMapper::toBackendBannerListingOutput)
                .let<Page<BackendBannerListingOutput>, PageResponse<BackendBannerListingOutput>>(RestResponse::page)
        }
    }

    @Locked("%{#banner.id}")
    override fun updateFields(banner: Banner, fields: UpdateBannerInput) {
        fields.name?.also { banner.name = it }
        fields.relatedSite?.also { banner.relatedSite = it }
        save(banner)
    }

    @Locked("%{#banner.id}")
    override fun updateWebImages(banner: Banner, body: UpdateBannerImageInput) {
        body.webImage?.also { banner.webImage = it }
        body.appImage?.also { banner.appImage = it }
        save(banner)
    }

    @Locked("%{#id}")
    override fun delete(id: UUID) {
        findAndDelete(id)
    }
}
