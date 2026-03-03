package cn.lili.modules.system.service.impl;

import cn.lili.modules.system.service.ExchangeRateProvider;
import cn.lili.modules.system.service.MaollarTierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public double calculateFund(double orderAmountUSD) {
        return orderAmountUSD * 0.1;
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
}
