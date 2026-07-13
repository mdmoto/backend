package cn.lili.modules.payment.kit.plugin.stripe;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripePluginSignatureTest {

    @Test
    void acceptsValidSignatureAndRejectsMalformedTimestamp() throws Exception {
        StripePlugin plugin = new StripePlugin();
        String payload = "{\"id\":\"evt_test\"}";
        String secret = "whsec_test_secret";
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = hmac(timestamp + "." + payload, secret);

        Boolean valid = ReflectionTestUtils.invokeMethod(
                plugin,
                "validStripeSignature",
                payload,
                "t=" + timestamp + ",v1=" + signature,
                secret
        );
        Boolean malformed = ReflectionTestUtils.invokeMethod(
                plugin,
                "validStripeSignature",
                payload,
                "t=not-a-number,v1=" + signature,
                secret
        );

        assertTrue(Boolean.TRUE.equals(valid));
        assertFalse(Boolean.TRUE.equals(malformed));
    }

    private String hmac(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
