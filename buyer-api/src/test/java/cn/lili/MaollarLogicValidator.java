package cn.lili;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Maollar 核心逻辑验证器 (不依赖 Lombok/Spring)
 */
public class MaollarLogicValidator {

    public static void main(String[] args) {
        System.out.println("=== Maollar 核心逻辑压力测试 (0-用户阶段) ===");

        // 模拟测试场景
        testTierLogic(0.0, 1, 1000.0);       // 冷启动
        testTierLogic(50000.0, 1, 1000.0);   // 第一笔订单 ($500) -> 50K units? No, UNIT is 100K
        testTierLogic(100000.0, 1, 1000.0);  // 临界点 $100K
        testTierLogic(100001.0, 2, 1000.0);  // 跨越第一档 (Fib(1)=1, Fib(2)=1 -> Width=1? No, logic uses n)
        
        System.out.println("\n=== 审计告警阈值验证 ===");
        verifyAuditThreshold(100.0, 5000.0);    // 正常比例 (50 MAO/USD)
        verifyAuditThreshold(10.0, 200000.0);   // 异常比例 (20000 MAO/USD)
    }

    private static void testTierLogic(double totalSalesUSD, int expectedTier, double expectedRate) {
        double UNIT = 100000.0;
        double currentUnits = totalSalesUSD / UNIT;
        
        int n = 1;
        int tier = 0;
        double rate = 0;
        
        while (n < 50) {
            long threshold = calculateFibonacci(n);
            if (currentUnits <= (double) threshold) {
                tier = n;
                long prevThreshold = calculateFibonacci(n - 1);
                long width = threshold - prevThreshold;
                rate = 1000.0 / (double) Math.max(1, width);
                break;
            }
            n++;
        }

        System.out.printf("Sales: $%.2f | Tier: %d (Exp: %d) | Rate: %.2f (Exp: %.2f) -> %s\n", 
            totalSalesUSD, tier, expectedTier, rate, expectedRate, 
            (tier == expectedTier && Math.abs(rate - expectedRate) < 0.01 ? "✅ PASS" : "❌ FAIL"));
    }

    private static long calculateFibonacci(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;
        long a = 1, b = 1;
        for (int i = 2; i <= n; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    private static void verifyAuditThreshold(double sales, double issued) {
        double ratio = (sales > 0) ? (issued / sales) : 0;
        boolean alarm = (sales > 0 && ratio > 10000) || (sales == 0 && issued > 0);
        System.out.printf("Audit - Sales: $%.2f | Issued: %.2f | Ratio: %.2f | Alarm: %s\n", 
            sales, issued, ratio, (alarm ? "🚨 TRIGGERED" : "🟢 CLEAR"));
    }
}
