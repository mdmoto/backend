package cn.lili.modules.payment.service;

import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;

/**
 * Stripe 支付快照服务
 */
public interface StripePaymentSnapshotService extends IService<StripePaymentSnapshot> {

    /**
     * 根据订单编号获取已确认的 Stripe 支付快照
     * 
     * @param orderSn 订单编号
     * @return Stripe 支付快照
     */
    StripePaymentSnapshot getConfirmedSnapshot(String orderSn);

    /**
     * 获取累计已确认的真实销售额 (USD)
     * 
     * @return 累计销售额 (USD)
     */
    double getCompletedTotalSalesUSD();

    /**
     * 获取累计已确认的真实销售额 (USD)，使用 BigDecimal 避免发币计算中的 double 精度损耗。
     *
     * @return 累计销售额 (USD)
     */
    BigDecimal getCompletedTotalSalesAmountUSD();

    /**
     * 获取截至指定 Stripe 快照位置的累计已确认真实销售额 (USD)。
     * 用于让跨档发币按客观支付快照顺序复算，而不是受异步完成事件触发时间影响。
     *
     * @param snapshot 当前订单的 Stripe 支付快照
     * @return 截至该快照的累计销售额 (USD)
     */
    BigDecimal getCompletedTotalSalesAmountUSDUpTo(StripePaymentSnapshot snapshot);
}
