package cn.lili.timetask.handler.impl.maollar;

import cn.lili.modules.member.service.MaoWithdrawalService;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.timetask.handler.EveryHourExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maollar 资金对账审计任务 (每小时执行)
 * 用于监控平台销售额与 $MAO 发放量之间的对账关系
 */
@Slf4j
@Component
public class MaollarAuditTaskExecute implements EveryHourExecute {

    @Autowired
    private StripePaymentSnapshotService stripePaymentSnapshotService;

    @Autowired
    private MaoWithdrawalService maoWithdrawalService;

    @Override
    public void execute() {
        log.info("【Maollar 审计】开始执行资金对账审计...");
        try {
            // 1. 获取 Stripe 实际收到的美元净额
            double totalSalesUSD = stripePaymentSnapshotService.getCompletedTotalSalesUSD();
            
            // 2. 获取链上已成功发放的 $MAO 总量
            double totalIssuedMAO = maoWithdrawalService.getTotalIssuedAmount();
            
            log.info("【Maollar 审计】当前统计 - 累计 GMV (USD): {}, 累计已发放 $MAO: {}", totalSalesUSD, totalIssuedMAO);
            
            // 3. 基础风控审计
            // 正常情况下，发放量不应无限制偏离 GMV (根据经济模型，早期 1 USD 对应约 1000 积分)
            // 如果出现 1 USD 对应了 10万 MAO，说明可能存在计算错误或刷单漏洞
            if (totalSalesUSD > 0 && (totalIssuedMAO / totalSalesUSD) > 10000) {
                log.error("【审计告警】$MAO 发放比例异常偏高！当前比例: {} MAO/USD。请立即检查系统流水。", (totalIssuedMAO / totalSalesUSD));
            } else if (totalSalesUSD == 0 && totalIssuedMAO > 0) {
                log.error("【审计告警】平台尚无销售记录，但已存在 $MAO 发放记录！");
            } else {
                log.info("【Maollar 审计】资金流水在正常阈值范围内。");
            }
            
        } catch (Exception e) {
            log.error("【Maollar 审计】对账任务执行失败", e);
        }
    }
}
