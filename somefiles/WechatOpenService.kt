package com.musicbible.service

import com.musicbible.config.properties.WechatOpenProperties
import com.musicbible.mapper.user.AppWechatinfoInput
import me.chanjar.weixin.mp.api.WxMpInMemoryConfigStorage
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl

class WechatOpenService(wechatOpenProperties: WechatOpenProperties) : WxMpServiceImpl() {

    init {
        val storageConfig = WxMpInMemoryConfigStorage()
            .also {
                it.appId = wechatOpenProperties.appid
                it.secret = wechatOpenProperties.secret
            }
        setWxMpConfigStorage(storageConfig)
    }

    fun code2Info(code: String): AppWechatinfoInput {
        val info = oauth2getAccessToken(code)
            .let {
                val userinfo = oauth2getUserInfo(it, null)
                AppWechatinfoInput(userinfo.openId,
                    userinfo.unionId,
                    userinfo.nickname,
                    userinfo.sex.toString(),
                    userinfo.headImgUrl)
            }
        return info
    }
}
