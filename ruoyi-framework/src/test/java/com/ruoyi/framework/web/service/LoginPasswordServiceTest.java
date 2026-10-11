package com.ruoyi.framework.web.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

/** 加密协议验证，不连接数据库或Redis。 */
class LoginPasswordServiceTest
{
    private static KeyPair pair;
    private static LoginPasswordService service;

    @BeforeAll
    static void setup() throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        service = new LoginPasswordService("-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
    }

    @Test
    void decryptsUtf8PasswordAndDerivesMatchingPublicKey() throws Exception
    {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, pair.getPublic(), new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        String password = "密码Abc123!";
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        assertEquals(password, service.decrypt(encrypted));
        String publicKey = service.getPublicKey().replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        assertArrayEquals(pair.getPublic().getEncoded(), Base64.getDecoder().decode(publicKey));
    }

    @Test
    void rejectsPlaintextMissingAndCorruptCiphertext()
    {
        assertThrows(ServiceException.class, () -> service.decrypt("admin123"));
        assertThrows(ServiceException.class, () -> service.decrypt(null));
        assertThrows(ServiceException.class, () -> service.decrypt("!".repeat(344)));
        assertThrows(ServiceException.class, () -> service.decrypt(Base64.getEncoder().encodeToString(new byte[256])));
    }

    @Test
    void invalidKeyPreventsInitialization()
    {
        assertThrows(IllegalStateException.class, () -> new LoginPasswordService("invalid-key"));
    }
}
