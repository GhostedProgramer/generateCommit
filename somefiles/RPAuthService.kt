package com.musicbible.service

import com.aliyun.oss.ClientException
import com.aliyuncs.AcsRequest
import com.aliyuncs.AcsResponse
import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.cloudauth.model.v20180916.GetMaterialsRequest
import com.aliyuncs.cloudauth.model.v20180916.GetStatusRequest
import com.aliyuncs.cloudauth.model.v20180916.GetVerifyTokenRequest
import com.aliyuncs.exceptions.ServerException
import com.aliyuncs.profile.DefaultProfile
import com.boostfield.extension.exception.stackTraceString
import com.boostfield.spring.exception.AppError
import com.musicbible.config.properties.RPAuthProperties
import com.musicbible.mapper.rpauth.RPAuthOutput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

data class IdCard(
    val name: String,
    val idNumber: String,
    val idCardType: String,
    val idCardStartDate: String,
    val idCardExpiry: String,
    val address: String,
    val sex: String,
    val ethnicGroup: String,
    val authority: String
)

interface RPAuthService {
    fun getRPManualToken(): RPAuthOutput
    fun getRPMaterial(ticket: String): IdCard
    fun getRPStatus(ticket: String): Int
}

@Service
class RPAuthServiceImpl(
    @Autowired val rpAuthProperties: RPAuthProperties
) : RPAuthService {
    val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    override fun getRPManualToken(): RPAuthOutput {
        val ticket = UUID.randomUUID().toString()
        return exchange({
            val request = GetVerifyTokenRequest()
            request.biz = rpAuthProperties.biz
            request.ticketId = ticket
            request
        },
            {
                RPAuthOutput(it.data.verifyToken.token, ticket)
            })
    }

    @Suppress("MagicNumber")
    override fun getRPMaterial(ticket: String): IdCard {
        val status = getRPStatus(ticket)
        if (status != 1) {
            logger.warn("认证请求[$ticket]当前状态[$status]")
            throw AppError.BadRequest.illegalOperate("请实名认证已通过")
        }
        return exchange({
            val request = GetMaterialsRequest()
            request.biz = rpAuthProperties.biz
            request.ticketId = ticket
            request
        },
            {
                val data = it.data
                IdCard(
                    data.name,
                    data.identificationNumber,
                    data.idCardType,
                    data.idCardStartDate,
                    data.idCardExpiry,
                    data.address,
                    data.sex,
                    data.ethnicGroup,
                    data.authority
                )
            })
    }

    /**
     *  认证任务所处的认证状态，取值：
     *    -1： 未认证。表示没有提交记录。
     *    0： 认证中。表示已提交认证，系统正在审核认证资料。
     *    1： 认证通过。表示最近一次提交的认证资料已通过审核，当前认证任务完结。
     *    2： 认证不通过。表示最近一次提交的认证资料未通过审核，当前认证任务还可以继续发起提交。
     */
    override fun getRPStatus(ticket: String): Int {
        return exchange({
            val request = GetStatusRequest()
            request.biz = rpAuthProperties.biz
            request.ticketId = ticket
            request
        },
            {
                it.data.statusCode
            })
    }

    private fun <T : AcsResponse, R> exchange(buildRequest: () -> AcsRequest<T>, parseResponse: (response: T) -> R): R {
        val profile = DefaultProfile.getProfile(
            rpAuthProperties.regionId,
            rpAuthProperties.accessKeyId,
            rpAuthProperties.accessKeySecret
        )
        val client = DefaultAcsClient(profile)
        try {
            return parseResponse(client.getAcsResponse(buildRequest()))
        } catch (e: ClientException) {
            logger.error("${e.message}: ${e.stackTraceString}")
            throw AppError.Internal.default(msg = "调用阿里云实人认证服务失败")

        } catch (e: ServerException) {
            logger.error("${e.message}: ${e.stackTraceString}")
            throw AppError.ServiceUnavailable.default(msg = "阿里云实人认证服务失效")
        }
    }
}