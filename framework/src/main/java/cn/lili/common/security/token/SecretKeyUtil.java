package cn.lili.common.security.token;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.SecretKey;

/**
 * SignWithUtil
 *
 * @author Chopper
 * @version v1.0
 * 2020-11-18 17:30
 */
@Slf4j
public class SecretKeyUtil {
    public static SecretKey generalKey() {
        String secret = System.getenv("LILI_JWT_SECRET_BASE64");
        if (secret == null || secret.trim().isEmpty()) {
            log.warn("LILI_JWT_SECRET_BASE64 not found in environment, using default fallback secret. PLEASE CONFIGURE THIS IN PRODUCTION!");
            secret = "bG9uZy10ZXJtLXNlY3JldC1rZXktZm9yLWxpbGlzaG9wLWFwcGxpY2F0aW9uLWJhc2U2NA=="; // "long-term-secret-key-for-lilishop-application-base64"
        }
        byte[] encodedKey = Base64.decodeBase64(secret);
        return Keys.hmacShaKeyFor(encodedKey);
    }

    public static SecretKey generalKeyByDecoders() {
        String secret = System.getenv("LILI_JWT_SECRET_BASE64");
        if (secret == null || secret.trim().isEmpty()) {
            log.warn("LILI_JWT_SECRET_BASE64 not found in environment, using default fallback secret. PLEASE CONFIGURE THIS IN PRODUCTION!");
            secret = "bG9uZy10ZXJtLXNlY3JldC1rZXktZm9yLWxpbGlzaG9wLWFwcGxpY2F0aW9uLWJhc2U2NA=="; // "long-term-secret-key-for-lilishop-application-base64"
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

}
