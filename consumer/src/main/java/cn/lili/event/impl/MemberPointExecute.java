package cn.lili.event.impl;

import cn.lili.event.AfterSaleStatusChangeEvent;
import cn.lili.event.GoodsCommentCompleteEvent;
import cn.lili.event.MemberRegisterEvent;
import cn.lili.event.OrderStatusChangeEvent;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.entity.dos.MemberEvaluation;
import cn.lili.modules.member.entity.enums.PointTypeEnum;
import cn.lili.modules.member.service.MemberPointsHistoryService;
import cn.lili.modules.member.service.MemberService;
import cn.lili.modules.member.entity.dos.MemberPointsHistory;

import cn.lili.modules.order.aftersale.entity.dos.AfterSale;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.dto.OrderMessage;
import cn.lili.modules.order.order.entity.enums.OrderPromotionTypeEnum;
import cn.lili.modules.order.trade.entity.enums.AfterSaleStatusEnum;
import cn.lili.modules.system.service.MaollarTierService;
import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 会员积分 (喵币 Meow Coin) 执行器
 * 核心逻辑：
 * 1. 使用动态斐波那契算法确定当前汇率
 * 2. 所有金额先转为 USD 进行计算，确保全球统一
 * 3. 喵币保留 6 位小数点精度 (底层存储为 micro-coin)
 */
@Slf4j
@Service
public class MemberPointExecute
        implements MemberRegisterEvent, GoodsCommentCompleteEvent, OrderStatusChangeEvent, AfterSaleStatusChangeEvent {

    @Autowired
    private MemberService memberService;
    @Autowired
    private cn.lili.modules.order.order.service.OrderService orderService;
    @Autowired
    private MaollarTierService maollarTierService;
    @Autowired
    private MemberPointsHistoryService memberPointsHistoryService;
    @Autowired
    private StripePaymentSnapshotService stripePaymentSnapshotService;
    @Autowired
    private cn.lili.cache.Cache cache;

    // 喵币精度：6 位 (1.000000)
    private static final long MEOW_COIN_SCALE = 1000000L;

    @Override
    public void memberRegister(Member member) {
    }

    @Override
    public void goodsComment(MemberEvaluation memberEvaluation) {
    }

    /**
     * 订单状态变更赠送/回退积分
     */
    @Override
    public void orderChange(OrderMessage orderMessage) {
        switch (orderMessage.getNewStatus()) {
            case CANCELLED: {
                Order order = orderService.getBySn(orderMessage.getOrderSn());
                Long point = order.getPriceDetailDTO().getPayPoint();
                if (point == null || point <= 0)
                    return;

                String content = "订单取消，喵币返还：" + formatPoint(point);
                memberService.updateMemberPoint(point, PointTypeEnum.INCREASE.name(), order.getMemberId(), content,
                        "RETURN_CANCEL_" + order.getSn(), java.math.BigDecimal.ZERO);


                break;
            }
            case COMPLETED: {
                Order order = orderService.getBySn(orderMessage.getOrderSn());
                if (order.getOrderPromotionType() != null
                        && order.getOrderPromotionType().equals(OrderPromotionTypeEnum.POINTS.name())) {
                    return;
                }

                // 1. 获取基于 Stripe 真实收款的全局销售额 (USD) 用于确定汇率
                double totalSalesUSD = stripePaymentSnapshotService.getCompletedTotalSalesUSD();
                double rate = maollarTierService.getCurrentRate(totalSalesUSD);

                // 2. 获取本单 Stripe 真实收款快照 (USD)
                StripePaymentSnapshot snapshot = stripePaymentSnapshotService.getConfirmedSnapshot(order.getSn());
                
                if (snapshot == null || snapshot.getAmountNetUsd() == null) {
                    log.warn("【Mao Mall 计价告警】订单 {} 尚未确认 Stripe 真实收款或金额为空，暂停发币。请确保 Stripe Webhook 已同步快照。", order.getSn());
                    // 增加遗漏计数器用于监控告警/巡检
                    cache.incr("maocoin_snapshot_missing_total");
                    return;
                }

                BigDecimal amountNetUsd = snapshot.getAmountNetUsd();
                // 仅对已确认 (CONFIRMED) 且金额大于 0 (amount_net_usd > 0) 的收款发币
                if (amountNetUsd.compareTo(BigDecimal.ZERO) <= 0) {
                    log.info("【Mao Mall 计价跳过】订单 {} 的 Stripe 快照金额为 {}，跳过发币。", order.getSn(), amountNetUsd);
                    return;
                }
                
                BigDecimal fundReserve = amountNetUsd.multiply(new BigDecimal("0.1")).setScale(8, RoundingMode.HALF_UP);

                // 4. 计算赠送喵币 (真实 USD * 汇率 * 精度)
                // 采用 HALF_UP 舍入，确保财务逻辑一致性
                long point = amountNetUsd.multiply(BigDecimal.valueOf(rate))
                                        .multiply(BigDecimal.valueOf(MEOW_COIN_SCALE))
                                        .setScale(0, RoundingMode.HALF_UP)
                                        .longValue();

                if (point > 0) {
                    memberService.updateMemberPoint(point, PointTypeEnum.INCREASE.name(), order.getMemberId(),
                            "喵领计划：基于 Stripe 真实收款赠送 " + formatPoint(point), "REWARD_MEOW_" + order.getSn(), fundReserve);
                }

                checkSettlementReminder();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void afterSaleStatusChange(AfterSale afterSale) {
        if (afterSale.getServiceStatus().equals(AfterSaleStatusEnum.COMPLETE.name())) {
            // P0 Fix: Avoid "Rate Arbitrage" by looking up the actual points issued for the original order
            Order order = orderService.getBySn(afterSale.getOrderSn());
            if (order == null) return;

            // P0 Fix: 使用 bizId + pointType 作为主条件，不再强匹配 content 前缀
            MemberPointsHistory originalHistory = memberPointsHistoryService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberPointsHistory>()
                    .eq(MemberPointsHistory::getMemberId, afterSale.getMemberId())
                    .eq(MemberPointsHistory::getBizId, "REWARD_MEOW_" + order.getSn())
                    .eq(MemberPointsHistory::getPointType, PointTypeEnum.INCREASE.name()));

            long pointToReduce;
            java.math.BigDecimal fundToReduce;

            if (originalHistory != null && originalHistory.getVariablePoint() > 0) {
                // 基于 Stripe 收款快照按真实比例扣回
                StripePaymentSnapshot snapshot = stripePaymentSnapshotService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StripePaymentSnapshot>()
                        .eq(StripePaymentSnapshot::getOrderSn, order.getSn())
                        .isNotNull(StripePaymentSnapshot::getAmountNetUsd), false);

                // P1 Fix: 增加退款比例边界钳制 [0, 1]
                double refundRatio = afterSale.getActualRefundPrice() / order.getFlowPrice();
                refundRatio = Math.max(0, Math.min(1, refundRatio));

                if (snapshot != null && snapshot.getAmountNetUsd().compareTo(BigDecimal.ZERO) > 0) {
                    pointToReduce = (long) (originalHistory.getVariablePoint() * refundRatio);
                    fundToReduce = originalHistory.getFundReserve().multiply(BigDecimal.valueOf(refundRatio)).setScale(8, RoundingMode.HALF_UP);
                } else {
                    // Fallback
                    pointToReduce = (long) (originalHistory.getVariablePoint() * refundRatio);
                    fundToReduce = originalHistory.getFundReserve().multiply(BigDecimal.valueOf(refundRatio)).setScale(8, RoundingMode.HALF_UP);
                }
            } else {
                pointToReduce = 0L;
                fundToReduce = BigDecimal.ZERO;
            }

            if (pointToReduce > 0) {
                memberService.updateMemberPoint(pointToReduce, PointTypeEnum.REDUCE.name(), afterSale.getMemberId(),
                        "售后完成，按 Stripe 退款比例回退 " + formatPoint(pointToReduce), "RETURN_REFUND_" + afterSale.getSn(), fundToReduce.negate());
            }


            checkSettlementReminder();
        }
    }


    private void checkSettlementReminder() {
        double unsettledLiability = memberPointsHistoryService.getUnsettledLiability();
        if (maollarTierService.shouldRemindSettlement(unsettledLiability)) {
            log.warn("【Mao Mall 财务预警】累计未拨付应拨备金已达 ${}。请尽快执行结算流程。", unsettledLiability);
        }
    }

    /**
     * 格式化积分用于日志/文案展示 (展示 6 位小数)
     */
    private String formatPoint(long point) {
        return BigDecimal.valueOf(point)
                .divide(BigDecimal.valueOf(MEOW_COIN_SCALE), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
