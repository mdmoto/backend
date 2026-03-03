package cn.lili.modules.system.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.system.service.ExchangeRateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open Exchange Rates 实现
 * 带有 4 小时缓存逻辑，防止频繁调用
 */
@Slf4j
@Service
public class OpenExchangeRatesProvider implements ExchangeRateProvider {

    @Value("${lili.maollar.oer.app-id:}")
    private String appId;

    private static final String API_URL = "https://openexchangerates.org/api/latest.json?app_id=";

    // 缓存：Key 为币种，Value 为 1 USD = 多少该币种 (OER 的标准格式)
    private final Map<String, BigDecimal> ratesFromUsdCache = new ConcurrentHashMap<>();
    private long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 4 * 3600 * 1000; // 4 小时

    @Override
    public BigDecimal getRateToUsd(String currencyCode) {
        if ("USD".equalsIgnoreCase(currencyCode))
            return BigDecimal.ONE;

        refreshRatesIfExpired(false);

        BigDecimal rateFromUsd = ratesFromUsdCache.get(currencyCode.toUpperCase());
        if (rateFromUsd == null || rateFromUsd.compareTo(BigDecimal.ZERO) == 0) {
            // 兜底方案：如果 API 挂了，使用之前的硬编码值防止系统瘫痪
            if ("CNY".equalsIgnoreCase(currencyCode))
                return new BigDecimal("0.14");
            if ("JPY".equalsIgnoreCase(currencyCode))
                return new BigDecimal("0.0065");
            return BigDecimal.ZERO;
        }

        // 计算 1 该法币 = 多少 USD (1 / x)
        return BigDecimal.ONE.divide(rateFromUsd, 8, RoundingMode.HALF_DOWN);
    }

    public synchronized void refreshRatesIfExpired(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastUpdateTime < CACHE_DURATION && !ratesFromUsdCache.isEmpty()) {
            return;
        }

        if (appId == null || appId.isEmpty()) {
            log.error("OpenExchangeRates AppID is missing! Please configure lili.maollar.oer.app-id");
            return;
        }

        try {
            String result = HttpUtil.get(API_URL + appId);
            JSONObject json = JSONUtil.parseObj(result);
            JSONObject rates = json.getJSONObject("rates");

            if (rates != null) {
                for (String key : rates.keySet()) {
                    ratesFromUsdCache.put(key, rates.getBigDecimal(key));
                }
                lastUpdateTime = now;
                log.info("Successfully refreshed exchange rates from Open Exchange Rates.");
            }
        } catch (Exception e) {
            log.error("Failed to fetch exchange rates: {}", e.getMessage());
        }
    }
}
