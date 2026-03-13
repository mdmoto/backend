package cn.lili.modules.payment.service;

import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

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
}
