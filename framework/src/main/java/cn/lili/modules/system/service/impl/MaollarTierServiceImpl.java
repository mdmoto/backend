package cn.lili.modules.system.service.impl;

import cn.lili.modules.system.service.ExchangeRateProvider;
import cn.lili.modules.system.service.MaollarTierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.lili.modules.system.service.SettingService;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.entity.dto.MaollarMilestoneSetting;
import cn.hutool.json.JSONUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.lili.modules.system.entity.dos.Setting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Maollar 档位积分服务实现 (实时汇率 + 安全垫版)
 */
@Slf4j
@Service
public class MaollarTierServiceImpl implements MaollarTierService {

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    @Autowired
    private SettingService settingService;

    // TODO: Move these to Nacos config or application.yml
    private static final String WORKER_URL = "https://mao-signing-worker.yourdomain.workers.dev";
    private static final String GATEWAY_SECRET = "CHANGE_ME_TO_REAL_SECRET";

    private static final double UNIT = 100000.0;
    private static final double POINTS_PER_TIER = 100000000.0;
    private static final long MEOW_COIN_SCALE = 1000000L;

    // 1% 的汇率波动冗余 (Safety Buffer)
    // 扣除这部分是为了防止汇率波动导致应拨备金不足
    private static final BigDecimal SAFETY_BUFFER = new BigDecimal("0.99");

    @Override
    public double getCurrentRate(double totalSalesUSD) {
        if (totalSalesUSD <= 0)
            return 1000.0;
        double currentUnits = totalSalesUSD / UNIT;
        int n = 1;
        while (n < 50) {
            long threshold = calculateFibonacci(n);
            if (currentUnits <= (double) threshold) {
                long prevThreshold = calculateFibonacci(n - 1);
                long width = threshold - prevThreshold;
                return 1000.0 / (double) Math.max(1, width);
            }
            n++;
        }
        return 1.0;
    }

    private long calculateFibonacci(int n) {
        if (n <= 0)
            return 0;
        if (n == 1)
            return 1;
        long a = 1;
        long b = 1;
        for (int i = 2; i <= n; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    @Override
    public double convertToUSD(double amount, String currency) {
        if (currency == null || currency.isEmpty() || "USD".equalsIgnoreCase(currency))
            return amount;

        // 获取实时市场汇率
        BigDecimal marketRate = exchangeRateProvider.getRateToUsd(currency);

        // 加上安全垫：USD价值 = 法币金额 * (汇率 * 0.99)
        // 这样计算出的 USD 价值会略低，意味着发出的积分会稍微少一点，从而保护池子安全性
        BigDecimal safeRate = marketRate.multiply(SAFETY_BUFFER);

        return BigDecimal.valueOf(amount)
                .multiply(safeRate)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    public double getFundRate(double totalSalesUSD) {
        if (totalSalesUSD <= 0)
            return 0.10;
        double currentUnits = totalSalesUSD / UNIT;
        int n = 1;
        while (n < 50) {
            long threshold = calculateFibonacci(n);
            if (currentUnits <= (double) threshold) {
                // 每达到一个里程碑降低 0.2%
                // n=1 (0-100K) -> 10.0% - (1-1)*0.2% = 10.0%
                // n=2 (100K-200K) -> 10.0% - (2-1)*0.2% = 9.8%
                // n=3 (200K-300K) -> 10.0% - (3-1)*0.2% = 9.6%
                double rate = 0.10 - (n - 1) * 0.002;
                return Math.max(0.005, rate); // 设置 0.5% 为保底比例
            }
            n++;
        }
        return 0.01;
    }

    @Override
    public double calculateFund(double orderAmountUSD, double totalSalesUSD) {
        return orderAmountUSD * getFundRate(totalSalesUSD);
    }

    @Override
    public boolean shouldRemindSettlement(double unsettledLiabilityUSD) {
        return unsettledLiabilityUSD >= 100000.0;
    }

    @Override
    public Map<String, Object> getTierStatus(double totalSalesUSD) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalSalesUSD", totalSalesUSD);
        double currentUnits = totalSalesUSD / UNIT;
        int n = 1;
        while (n < 50) {
            long threshold = calculateFibonacci(n);
            if (currentUnits <= (double) threshold) {
                long prevThreshold = calculateFibonacci(n - 1);
                double tierWidth = (double) (threshold - prevThreshold);
                double progressInTier = (tierWidth <= 0) ? 1.0 : (currentUnits - prevThreshold) / tierWidth;
                result.put("tier", n);
                double remainingPoints = POINTS_PER_TIER * (1.0 - progressInTier) * MEOW_COIN_SCALE;
                result.put("remainingPoints", Math.max(0, remainingPoints));
                break;
            }
            n++;
        }
        result.put("pointsPerTier", POINTS_PER_TIER * MEOW_COIN_SCALE);
        result.put("decimals", 6);
        return result;
    }

    /**
     * 检查并触发自动增发
     * @param totalSalesUSD 当前平台总销售额
     */
    public void checkAndTriggerMilestone(double totalSalesUSD) {
        double currentUnits = totalSalesUSD / UNIT;
        int expectedMilestone = 0;
        
        // 查找当前应达到的最高里程碑
        int n = 1;
        while (n < 50) {
            long threshold = calculateFibonacci(n);
            if (currentUnits >= (double) threshold) {
                expectedMilestone = n;
            } else {
                break;
            }
            n++;
        }
        
        Setting setting = settingService.get(SettingEnum.MAOLLAR_MILESTONE_SETTING.name());
        MaollarMilestoneSetting milestoneSetting;
        if (setting == null || setting.getSettingValue() == null) {
            milestoneSetting = new MaollarMilestoneSetting();
        } else {
            milestoneSetting = JSONUtil.toBean(setting.getSettingValue(), MaollarMilestoneSetting.class);
        }

        if (expectedMilestone > milestoneSetting.getCurrentMilestoneMinted()) {
            log.info("【Maollar 增发】检测到销售额达到 {}，触发 Milestone {} 增发。", totalSalesUSD, expectedMilestone);
            
            // 1. 发送 HTTP 请求给 Cloudflare Worker
            boolean success = triggerWorkerMint(expectedMilestone);
            
            // 2. 如果触发成功，更新数据库记录
            if (success) {
                milestoneSetting.setCurrentMilestoneMinted(expectedMilestone);
                if (setting == null) {
                    setting = new Setting();
                    setting.setId(SettingEnum.MAOLLAR_MILESTONE_SETTING.name());
                }
                setting.setSettingValue(JSONUtil.toJsonStr(milestoneSetting));
                settingService.saveUpdate(setting);
                log.info("【Maollar 增发】Milestone {} 状态已保存。", expectedMilestone);
            }
        }
    }

    private boolean triggerWorkerMint(int milestone) {
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("type", "MINT_MILESTONE");
            bodyMap.put("milestone", milestone);
            String bodyText = JSONUtil.toJsonStr(bodyMap);

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // HMAC 签名
            HMac hMac = new HMac(HmacAlgorithm.HmacSHA256, GATEWAY_SECRET.getBytes());
            String signature = hMac.digestHex(timestamp + bodyText);

            // 发送请求
            String response = HttpUtil.createPost(WORKER_URL)
                    .header("X-MAO-Signature", signature)
                    .header("X-MAO-Timestamp", timestamp)
                    .body(bodyText)
                    .timeout(10000)
                    .execute().body();

            log.info("【Maollar 增发】Worker 响应: {}", response);
            return response.contains("\"success\":true");
        } catch (Exception e) {
            log.error("【Maollar 增发】调用 Worker 失败", e);
            return false;
        }
    }
}
