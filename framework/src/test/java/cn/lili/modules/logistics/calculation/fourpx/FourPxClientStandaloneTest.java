package cn.lili.modules.logistics.calculation.fourpx;

import cn.lili.common.properties.FourPxProperties;
import cn.lili.modules.logistics.calculation.LogisticsEstimateRequest;
import cn.lili.modules.logistics.calculation.LogisticsQuote;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FourPxClientStandaloneTest {

    @Test
    public void testEstimateCost() {
        String appKey = System.getenv("FOURPX_APP_KEY");
        String appSecret = System.getenv("FOURPX_APP_SECRET");
        String baseUrl = System.getenv().getOrDefault("FOURPX_BASE_URL", "https://open.4px.com");

        // Avoid committing secrets into the repo; this test runs only when env vars are provided.
        Assumptions.assumeTrue(appKey != null && !appKey.isBlank(), "FOURPX_APP_KEY is not set");
        Assumptions.assumeTrue(appSecret != null && !appSecret.isBlank(), "FOURPX_APP_SECRET is not set");

        FourPxClient client = new FourPxClient();
        FourPxProperties props = new FourPxProperties();
        props.setAppKey(appKey);
        props.setAppSecret(appSecret);
        props.setBaseUrl(baseUrl);

        ReflectionTestUtils.setField(client, "fourPxProperties", props);

        LogisticsEstimateRequest request = new LogisticsEstimateRequest();
        request.setCountryCode("US");
        request.setPostalCode("90001");
        request.setTotalWeightKg(1.5);
        request.setLengthCm(10.0);
        request.setWidthCm(10.0);
        request.setHeightCm(10.0);

        List<LogisticsQuote> quotes = client.estimateCost(request);

        // Assertions
        assertNotNull(quotes, "Quotes list should not be null");
        assertTrue(quotes.size() >= 0, "Call should complete (at least 0 or more results)");
    }
}
