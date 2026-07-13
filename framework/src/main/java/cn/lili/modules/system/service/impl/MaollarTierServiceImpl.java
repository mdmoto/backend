package cn.lili.modules.system.service.impl;

import cn.lili.modules.system.service.ExchangeRateProvider;
import cn.lili.modules.system.service.MaollarTierService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.lili.modules.system.service.SettingService;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.entity.dto.MaollarMilestoneSetting;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.lili.modules.system.entity.dos.Setting;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private RedissonClient redissonClient;

    @org.springframework.beans.factory.annotation.Value("${lili.maollar.solana.worker-url:https://mao-signing-gateway.mdmoto.workers.dev}")
    private String workerUrl;

    @org.springframework.beans.factory.annotation.Value("${lili.maollar.solana.gateway-secret:}")
    private String gatewaySecret;

    @org.springframework.beans.factory.annotation.Value("${lili.maollar.solana.mint-enabled:false}")
    private Boolean mintEnabled;

    private static final double UNIT = 100000.0;
    private static final double POINTS_PER_TIER = 100000000.0;
    private static final long MEOW_COIN_SCALE = 1000000L;
    private static final BigDecimal UNIT_USD = new BigDecimal("100000");
    private static final BigDecimal BASE_REWARD_RATE = new BigDecimal("1000");
    private static final BigDecimal MICRO_COIN_SCALE = new BigDecimal(MEOW_COIN_SCALE);
    private static final String MILESTONE_MINT_LOCK = "maollar:milestone:mint:lock";

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
        if (n == 2)
            return 2;
        long a = 1;
        long b = 2;
        for (int i = 3; i <= n; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    @Override
    public long calculateRewardPoints(BigDecimal orderAmountUSD, BigDecimal totalSalesBeforeUSD) {
        if (orderAmountUSD == null || orderAmountUSD.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }

        BigDecimal remaining = orderAmountUSD;
        BigDecimal cursor = normalizeSales(totalSalesBeforeUSD);
        BigDecimal reward = BigDecimal.ZERO;

        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            int tier = findTier(cursor);
            BigDecimal tierEnd = tierEndUsd(tier);
            BigDecimal segment = tierEnd.subtract(cursor);
            if (segment.compareTo(BigDecimal.ZERO) <= 0) {
                cursor = tierEnd;
                continue;
            }
            if (segment.compareTo(remaining) > 0) {
                segment = remaining;
            }

            reward = reward.add(segment.multiply(rewardRateByTier(tier)).multiply(MICRO_COIN_SCALE));
            cursor = cursor.add(segment);
            remaining = remaining.subtract(segment);
        }

        return reward.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    @Override
    public BigDecimal calculateFundReserve(BigDecimal orderAmountUSD, BigDecimal totalSalesBeforeUSD) {
        if (orderAmountUSD == null || orderAmountUSD.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }

        BigDecimal remaining = orderAmountUSD;
        BigDecimal cursor = normalizeSales(totalSalesBeforeUSD);
        BigDecimal fundReserve = BigDecimal.ZERO;

        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            int tier = findTier(cursor);
            BigDecimal tierEnd = tierEndUsd(tier);
            BigDecimal segment = tierEnd.subtract(cursor);
            if (segment.compareTo(BigDecimal.ZERO) <= 0) {
                cursor = tierEnd;
                continue;
            }
            if (segment.compareTo(remaining) > 0) {
                segment = remaining;
            }

            fundReserve = fundReserve.add(segment.multiply(BigDecimal.valueOf(fundRateByTier(tier))));
            cursor = cursor.add(segment);
            remaining = remaining.subtract(segment);
        }

        return fundReserve.setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeSales(BigDecimal totalSalesBeforeUSD) {
        if (totalSalesBeforeUSD == null || totalSalesBeforeUSD.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return totalSalesBeforeUSD;
    }

    private int findTier(BigDecimal salesUSD) {
        for (int n = 1; n < 50; n++) {
            if (salesUSD.compareTo(tierEndUsd(n)) < 0) {
                return n;
            }
        }
        return 50;
    }

    private BigDecimal tierEndUsd(int tier) {
        return BigDecimal.valueOf(calculateFibonacci(tier)).multiply(UNIT_USD);
    }

    private BigDecimal rewardRateByTier(int tier) {
        long prevThreshold = calculateFibonacci(tier - 1);
        long threshold = calculateFibonacci(tier);
        long width = Math.max(1, threshold - prevThreshold);
        return BASE_REWARD_RATE.divide(BigDecimal.valueOf(width), MathContext.DECIMAL64);
    }

    private double fundRateByTier(int tier) {
        double rate = 0.10 - (tier - 1) * 0.002;
        return Math.max(0.005, rate);
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
        if (totalSalesUSD <= 0) {
            return;
        }

        if (!Boolean.TRUE.equals(mintEnabled)) {
            log.info("【Maollar 增发】链上铸造未启用，跳过自动铸造检查。当前销售额: {}", totalSalesUSD);
            return;
        }
        if (CharSequenceUtil.isBlank(workerUrl) || CharSequenceUtil.isBlank(gatewaySecret)) {
            log.error("【Maollar 增发】Worker 网关或 HMAC 密钥未配置，拒绝触发自动铸造。");
            return;
        }

        RLock lock = redissonClient.getLock(MILESTONE_MINT_LOCK);
        boolean locked = false;
        try {
            locked = lock.tryLock(10, 300, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("【Maollar 增发】里程碑铸造锁被占用，本次检查跳过。当前销售额: {}", totalSalesUSD);
                return;
            }

            Setting setting = settingService.get(SettingEnum.MAOLLAR_MILESTONE_SETTING.name());
            MaollarMilestoneSetting milestoneSetting;
            if (setting == null || setting.getSettingValue() == null) {
                milestoneSetting = new MaollarMilestoneSetting();
            } else {
                milestoneSetting = JSONUtil.toBean(setting.getSettingValue(), MaollarMilestoneSetting.class);
            }

            if (!Boolean.TRUE.equals(milestoneSetting.getInitialMinted())) {
                log.info("【Maollar 首单兑换池】检测到首笔有效销售额 {}，触发兑换池 1 亿枚铸造。", totalSalesUSD);
                if (!triggerInitialDistributionPool(totalSalesUSD)) {
                    return;
                }
                milestoneSetting.setInitialMinted(true);
                setting = saveMilestoneSetting(setting, milestoneSetting);
                log.info("【Maollar 首单兑换池】兑换池初始 1 亿枚状态已保存。");
            }

            int expectedMilestone = findExpectedSalesMilestone(totalSalesUSD);
            int currentMilestone = milestoneSetting.getCurrentMilestoneMinted() == null
                    ? 0
                    : milestoneSetting.getCurrentMilestoneMinted();

            for (int milestone = currentMilestone + 1; milestone <= expectedMilestone; milestone++) {
                log.info("【Maollar 增发】检测到销售额达到 {}，触发 Milestone {} 增发。", totalSalesUSD, milestone);
                if (!triggerWorkerMint(milestone, "SALES_MILESTONE", totalSalesUSD)) {
                    return;
                }
                milestoneSetting.setCurrentMilestoneMinted(milestone);
                setting = saveMilestoneSetting(setting, milestoneSetting);
                log.info("【Maollar 增发】Milestone {} 状态已保存。", milestone);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【Maollar 增发】获取里程碑铸造锁被中断，本次检查跳过。", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int findExpectedSalesMilestone(double totalSalesUSD) {
        double currentUnits = totalSalesUSD / UNIT;
        int expectedMilestone = 0;

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
        return expectedMilestone;
    }

    private Setting saveMilestoneSetting(Setting setting, MaollarMilestoneSetting milestoneSetting) {
        if (setting == null) {
            setting = new Setting();
            setting.setId(SettingEnum.MAOLLAR_MILESTONE_SETTING.name());
        }
        setting.setSettingValue(JSONUtil.toJsonStr(milestoneSetting));
        settingService.saveUpdate(setting);
        return setting;
    }

    private boolean triggerInitialDistributionPool(double totalSalesUSD) {
        String requestId = "MINT_INITIAL_DISTRIBUTION_POOL";
        return triggerWorkerMint(requestId, "MINT_INITIAL_POOL", 0, "INITIAL_DISTRIBUTION_POOL", totalSalesUSD);
    }

    private boolean triggerWorkerMint(int milestone, String mintReason, double totalSalesUSD) {
        return triggerWorkerMint("MINT_MILESTONE_" + milestone, "MINT_MILESTONE", milestone, mintReason, totalSalesUSD);
    }

    private boolean triggerWorkerMint(String requestId, String type, int milestone, String mintReason, double totalSalesUSD) {
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("type", type);
            bodyMap.put("request_id", requestId);
            bodyMap.put("mint_reason", mintReason);
            bodyMap.put("milestone", milestone);
            bodyMap.put("total_sales_usd", totalSalesUSD);
            bodyMap.put("audit_hash", buildGmvAuditHash(requestId, totalSalesUSD));
            bodyMap.put("audit_scope", "STRIPE_COMPLETED_TOTAL_SALES_USD");
            String bodyText = JSONUtil.toJsonStr(bodyMap);

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // HMAC 签名
            HMac hMac = new HMac(HmacAlgorithm.HmacSHA256, gatewaySecret.getBytes());
            String signature = hMac.digestHex(timestamp + bodyText);

            // 发送请求
            String response = HttpUtil.createPost(workerUrl)
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

    private String buildGmvAuditHash(String requestId, double totalSalesUSD) {
        String normalizedSales = BigDecimal.valueOf(totalSalesUSD)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                "MAOLLAR_GMV_REPORT_V1|" + requestId + "|" + normalizedSales);
    }
}
