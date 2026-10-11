package com.ruoyi.framework.web.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;

/** 登录专用RSA-OAEP：私钥只在服务端保存，禁止明文降级。 */
@Service
public class LoginPasswordService
{
    private final RSAPrivateCrtKey privateKey;
    private final String publicKey;

    public LoginPasswordService(@Value("${login.password.private-key}") String pem)
    {
        try
        {
            String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (privateKey.getModulus().bitLength() < 2048)
            {
                throw new IllegalArgumentException("RSA key too short");
            }
            byte[] bytes = factory.generatePublic(new RSAPublicKeySpec(
                    privateKey.getModulus(), privateKey.getPublicExponent())).getEncoded();
            publicKey = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes)
                    + "\n-----END PUBLIC KEY-----";
        }
        catch (GeneralSecurityException | IllegalArgumentException | ClassCastException exception)
        {
            // 配置错误时停止启动，避免服务悄悄接受明文密码。
            throw new IllegalStateException("登录RSA私钥配置无效，请配置至少2048位的PKCS#8私钥");
        }
    }

    public String getPublicKey()
    {
        return publicKey;
    }

    /** 显式指定OAEP和MGF1均为SHA-256，与浏览器加密参数保持一致。 */
    public String decrypt(String encryptedPassword)
    {
        try
        {
            int size = (privateKey.getModulus().bitLength() + 7) / 8;
            if (encryptedPassword == null || encryptedPassword.length() != 4 * ((size + 2) / 3))
            {
                throw new IllegalArgumentException("Invalid ciphertext length");
            }
            byte[] encrypted = Base64.getDecoder().decode(encryptedPassword);
            if (encrypted.length != size)
            {
                throw new IllegalArgumentException("Invalid RSA block length");
            }
            // Cipher不能跨请求共享，每次解密创建独立实例。
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException | IllegalArgumentException exception)
        {
            // 不回显密文、明文或底层解密细节。
            throw new ServiceException("登录密码解密失败，请刷新页面后重试");
        }
    }
}
