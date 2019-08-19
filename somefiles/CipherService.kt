package com.musicbible.service

import com.musicbible.config.properties.CipherConfig
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

interface CipherService {

    fun encrypt(code: String): String
    fun decrypt(code: String): String
}

@Service
class CipherServiceImpl(
    @Autowired val cipherConfig: CipherConfig
) : CipherService {

    /*
    * @param code:待加密字符串
    * @return :使用AES算法加密,使用base64算法编码后的code
    * */
    override fun encrypt(code: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, getSpecificKey())
        return Base64.encodeBase64String(cipher.doFinal(code.toByteArray()))
    }

    /*
    * @param code:待解密字符串
    * @return :解密完成后的字符串
    * */
    override fun decrypt(code: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, getSpecificKey())
        return String(cipher.doFinal(Base64.decodeBase64(code)))
    }

    /*
    * 获取固定密钥
    * @return 根据myKey生成的固定密钥
    * */
    private fun getSpecificKey(): Key {
        // 生成Key密钥
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(@Suppress("MagicNumber") 128)
        val myKey = cipherConfig.key  //需为16位才满足AES算法需求
        return SecretKeySpec(myKey.toByteArray(), "AES")
    }
}
