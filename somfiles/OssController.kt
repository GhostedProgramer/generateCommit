package com.musicbible.controller

import com.boostfield.aliossspringbootstarter.AliOSSService
import com.boostfield.aliossspringbootstarter.OSSSignature
import com.boostfield.spring.exception.AppError
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/")
@Api(value = "/api/", tags = ["D 对象存储"], description = "OSS")
class OssController(
    @Autowired val aliOSSService: AliOSSService
) {
    @ApiOperation("获取签名")
    @GetMapping("/v0/oss/sign/{bucketName}")
    @Deprecated("废弃", replaceWith = ReplaceWith("/v1/oss/sign/{bucket}"))
    fun getOSSSignature(@PathVariable bucketName: String): OSSSignature {
        return aliOSSService.getSignature(bucketName)
    }

    @ApiOperation("获取相应桶签名")
    @ApiImplicitParam(name = "bucket", allowableValues = "doc,img,media,score", required = true, value = "doc:文档，img:图片,media:多媒体,score:乐谱")
    @GetMapping("/v1/oss/sign/{bucket}")
    fun getOSSSignature_v1(@PathVariable bucket: String): OSSSignature {
        val allows = listOf("doc", "img", "media", "score")
        if (allows.contains(bucket).not()) {
            throw AppError.BadRequest.paramError("不允许的取值:$bucket")
        }
        return aliOSSService.getSignature("hifi$bucket")
    }
}
