package cn.lili.timetask.handler.impl;

import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.enums.OrderStatusEnum;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.timetask.handler.EveryHourExecute;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stripe 快照覆盖率巡检
 * 监控过去 24h 已完成 Stripe 订单中 CONFIRMED 快照的缺失比例
 * 如果缺失比例超过 0.1% (ALARM_THRESHOLD)，则触发 ERROR 级别告警
 */
@Slf4j
@Component
public class StripeSnapshotCoverageExecute implements EveryHourExecute {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StripePaymentSnapshotService stripePaymentSnapshotService;

    @Autowired
    private cn.lili.modules.email.EmailUtil emailUtil;

    @Autowired
    private cn.lili.modules.system.service.SettingService settingService;

    // 报警阈值：缺失比例超过 0.1%
    private static final double ALARM_THRESHOLD = 0.001;
    // Keep this independent of PaymentMethodEnum so the production baseline can
    // receive the monitoring fix before the payment-integration branch is released.
    private static final String STRIPE_PAYMENT_METHOD = "STRIPE";

    @Override
    public void execute() {
        log.info("开始执行 Stripe 快照覆盖率巡检...");

        // 1. 只获取过去 24h 内完成的 Stripe 订单。其他支付渠道没有 Stripe
        // 快照，纳入统计会制造 100% 缺失的误报。
        long twentyFourHoursAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        LambdaQueryWrapper<Order> orderQuery = new LambdaQueryWrapper<>();
        orderQuery.eq(Order::getOrderStatus, OrderStatusEnum.COMPLETED.name())
                  .eq(Order::getPaymentMethod, STRIPE_PAYMENT_METHOD)
                  .ge(Order::getCompleteTime, new Date(twentyFourHoursAgo));
        
        List<Order> completedOrders = orderService.list(orderQuery);
        // Defensive filtering also protects tests/custom OrderService implementations that
        // do not evaluate the query wrapper themselves.
        if (completedOrders != null) {
            completedOrders = completedOrders.stream()
                    .filter(order -> STRIPE_PAYMENT_METHOD.equals(order.getPaymentMethod()))
                    .collect(Collectors.toList());
        }
        if (completedOrders == null || completedOrders.isEmpty()) {
            log.info("过去 24h 无已完成 Stripe 订单，跳过巡检。");
            return;
        }

        int totalCount = completedOrders.size();
        Set<String> orderSns = completedOrders.stream().map(Order::getSn).collect(Collectors.toSet());

        // 2. 查询对应的 CONFIRMED 快照
        LambdaQueryWrapper<StripePaymentSnapshot> snapshotQuery = new LambdaQueryWrapper<>();
        snapshotQuery.in(StripePaymentSnapshot::getOrderSn, orderSns)
                     .eq(StripePaymentSnapshot::getPaymentStatus, "CONFIRMED");
        
        List<StripePaymentSnapshot> snapshots = stripePaymentSnapshotService.list(snapshotQuery);
        Set<String> snapshotOrderSns = snapshots.stream().map(StripePaymentSnapshot::getOrderSn).collect(Collectors.toSet());

        // 3. 计算缺失数量
        int missingCount = 0;
        for (String sn : orderSns) {
            if (!snapshotOrderSns.contains(sn)) {
                missingCount++;
            }
        }

        double missingRatio = (double) missingCount / totalCount;

        log.info("【Stripe 覆盖率巡检】过去 24h 已完成订单数: {}, 缺失快照数: {}, 缺失比例: {}%", 
                totalCount, missingCount, String.format("%.2f", missingRatio * 100));

        // 4. 超阈值报警
        if (missingCount > 0 && missingRatio > ALARM_THRESHOLD) {
            String alertMsg = String.format("【Mao Mall 巡检告警】Stripe 快照覆盖率不足！过去 24h 缺失比例 %.2f%% 超过阈值 %.2f%%。缺失数量: %d/%d。请立即检查 Stripe Webhook 同步状态。", 
                    missingRatio * 100, ALARM_THRESHOLD * 100, missingCount, totalCount);
            
            log.error(alertMsg);

            // 发送邮件告警给系统管理员
            try {
                // 尝试获取站点配置中的管理员邮箱进行推送
                emailUtil.sendTestEmail("admin@maollar.com", "Mao Mall Alert: Stripe Snapshot Missing", alertMsg);
            } catch (Exception e) {
                log.error("发送告警邮件失败", e);
            }
        }
    }
}
