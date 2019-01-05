package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.musicbible.es.IDX_RELEASE
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v0/probe")
@Api("/api/v0/probe", tags = ["k8s探针"])
class ProbeController(
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val userService: UserService
) : BaseController() {

    val component = "backend"

    @ApiOperation("存活探针")
    @GetMapping("/alive")
    fun beLive(): String {
        return "$component live"
    }

    @ApiOperation("就绪探针")
    @GetMapping("/ready")
    fun beReady(): String {
        userService.count()
        elasticsearchTemplate.indexExists(IDX_RELEASE)
        return "$component ready"
    }
}
