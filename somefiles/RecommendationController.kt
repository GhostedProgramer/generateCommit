package com.musicbible.controller

import com.boostfield.spring.http.CreatedResponse
import com.boostfield.spring.http.PageResponse
import com.boostfield.spring.http.RestResponse
import com.musicbible.mapper.recommendation.CreateRecommendationInput
import com.musicbible.mapper.recommendation.RecommendationListInput
import com.musicbible.mapper.recommendation.RecommendationListingOutput
import com.musicbible.mapper.recommendation.RecommendationMapper
import com.musicbible.model.User
import com.musicbible.security.UserOrThrow
import com.musicbible.service.RecommendationService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/recommendation")
@Api(value = "/api/v0/recommendation", tags = ["X 小程序每日推荐"], description = "Recommendation")
class RecommendationController(
    @Autowired val recommendationMapper: RecommendationMapper,
    @Autowired val recommendationService: RecommendationService
) {

    @ApiOperation("新建")
    @PreAuthorize("hasAuthority('MANAGE_RECOMMEND')")
    @PostMapping
    fun create(@UserOrThrow user: User, @RequestBody input: CreateRecommendationInput): CreatedResponse {
        return RestResponse.created(recommendationService.create(user, input))
    }

    @ApiOperation("列表")
    @PreAuthorize("hasAuthority('MANAGE_RECOMMEND')")
    @GetMapping
    fun list(@Valid input: RecommendationListInput): PageResponse<RecommendationListingOutput> {
        return recommendationService.findList(input)
            .map(recommendationMapper::toRecommendationListingOutput)
            .let(RestResponse::page)
    }

    @ApiOperation("删除")
    @PreAuthorize("hasAuthority('MANAGE_RECOMMEND')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        recommendationService.remove(id)
    }
}
