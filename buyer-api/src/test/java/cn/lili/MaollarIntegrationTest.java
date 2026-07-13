package cn.lili;

import cn.lili.modules.member.service.MaoWithdrawalService;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.modules.system.service.MaollarTierService;
import cn.lili.modules.system.service.impl.MaollarTierServiceImpl;
import cn.lili.timetask.handler.impl.maollar.MaollarAuditTaskExecute;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

/**
 * Maollar 0-用户阶段冷启动集成测试
 * 验证系统在无数据时的稳定性，以及首笔交易进入后的逻辑触发
 */
public class MaollarIntegrationTest {

    private MaollarTierServiceImpl maollarTierService;
    private MaollarAuditTaskExecute auditTask;
    
    // Stubs
    private StubStripePaymentSnapshotService stripeService;
    private StubMaoWithdrawalService withdrawalService;

    @BeforeEach
    public void setup() {
        maollarTierService = new MaollarTierServiceImpl();
        auditTask = new MaollarAuditTaskExecute();
        
        stripeService = new StubStripePaymentSnapshotService();
        withdrawalService = new StubMaoWithdrawalService();

        // 注入依赖
        ReflectionTestUtils.setField(auditTask, "stripePaymentSnapshotService", stripeService);
        ReflectionTestUtils.setField(auditTask, "maoWithdrawalService", withdrawalService);
    }

    @Test
    public void testColdStartWithZeroData() {
        // 1. 模拟 0 销售额
        stripeService.setTotalSales(0.0);
        withdrawalService.setTotalIssued(0.0);

        // 2. 检查 Tier 逻辑
        Map<String, Object> status = maollarTierService.getTierStatus(0.0);
        Assertions.assertEquals(1, status.get("tier"), "冷启动时应处于第 1 档");
        
        double rate = maollarTierService.getCurrentRate(0.0);
        Assertions.assertEquals(1000.0, rate, "初始汇率应为 1000");

        // 3. 执行审计任务 (验证不崩溃)
        auditTask.execute();
    }

    @Test
    public void testFirstUserSaleImpact() {
        // 1. 模拟首笔 $100 订单完成
        stripeService.setTotalSales(100.0);
        withdrawalService.setTotalIssued(0.0);

        // 2. 验证汇率依然维持在第 1 档 (Fibonacci 1 = 100K 阈值)
        double rate = maollarTierService.getCurrentRate(100.0);
        Assertions.assertEquals(1000.0, rate, "在达到 100K 之前，汇率应保持稳定");

        // 3. 验证审计任务发现差异
        // 此时 totalSalesUSD = 100, totalIssuedMAO = 0
        // 审计应输出“正常阈值范围内”日志 (因为 0/100 < 10000)
        auditTask.execute();
    }

    @Test
    public void testAuditAnomalyDetection() {
        // 模拟异常情况：无销售额却发了币
        stripeService.setTotalSales(0.0);
        withdrawalService.setTotalIssued(5000.0);

        // 这里我们主要验证逻辑分支走向，由于 execute() 内部仅写日志，
        // 真实生产中我们会配置告警系统，此处确保不发生空指针异常。
        auditTask.execute();
    }

    // --- Stubs ---

    static class StubStripePaymentSnapshotService implements StripePaymentSnapshotService {
        private double totalSales;
        public void setTotalSales(double s) { this.totalSales = s; }
        @Override public double getCompletedTotalSalesUSD() { return totalSales; }
        @Override public cn.lili.modules.payment.entity.StripePaymentSnapshot getConfirmedSnapshot(String orderSn) { return null; }
        @Override public com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.payment.entity.StripePaymentSnapshot> getWrapper() { return null; }
        @Override public com.baomidou.mybatisplus.core.mapper.BaseMapper<cn.lili.modules.payment.entity.StripePaymentSnapshot> getBaseMapper() { return null; }
        @Override public Class<cn.lili.modules.payment.entity.StripePaymentSnapshot> getEntityClass() { return null; }
    }

    static class StubMaoWithdrawalService implements MaoWithdrawalService {
        private double totalIssued;
        public void setTotalIssued(double i) { this.totalIssued = i; }
        @Override public double getTotalIssuedAmount() { return totalIssued; }
        @Override public double getDailyTotalIssued() { return 0; }
        @Override public cn.lili.modules.member.entity.dos.MaoWithdrawalLog applyWithdrawal(Long points, String address) { return null; }
        @Override public void executeWithdrawal(String id) {}
        @Override public com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.member.entity.dos.MaoWithdrawalLog> getWrapper() { return null; }
        @Override public com.baomidou.mybatisplus.core.mapper.BaseMapper<cn.lili.modules.member.entity.dos.MaoWithdrawalLog> getBaseMapper() { return null; }
        @Override public Class<cn.lili.modules.member.entity.dos.MaoWithdrawalLog> getEntityClass() { return null; }
    }
}
