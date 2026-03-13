package cn.lili.common.security.token;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.SecretKey;

/**
 * SignWithUtil
 *
 * @author Chopper
 * @version v1.0
 * 2020-11-18 17:30
 */
public class SecretKeyUtil {
    public static SecretKey generalKey() {
        String secret = System.getenv("LILI_JWT_SECRET_BASE64");
        if (secret == null || secret.trim().isEmpty()) {
            throw new RuntimeException("CRITICAL SECURITY ERROR: Environment variable LILI_JWT_SECRET_BASE64 is missing! Insecure hardcoded keys are no longer allowed.");
        }
        byte[] encodedKey = Base64.decodeBase64(secret);
        return Keys.hmacShaKeyFor(encodedKey);
    }

    public static SecretKey generalKeyByDecoders() {
        String secret = System.getenv("LILI_JWT_SECRET_BASE64");
        if (secret == null || secret.trim().isEmpty()) {
             throw new RuntimeException("CRITICAL SECURITY ERROR: Environment variable LILI_JWT_SECRET_BASE64 is missing! Insecure hardcoded keys are no longer allowed.");
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

}
