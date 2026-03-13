package cn.lili.event.impl;

import cn.hutool.core.text.CharSequenceUtil;
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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import cn.lili.modules.order.aftersale.entity.dos.AfterSale;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.dto.OrderMessage;
import cn.lili.modules.order.order.entity.enums.OrderPromotionTypeEnum;
import cn.lili.modules.order.trade.entity.enums.AfterSaleStatusEnum;
import cn.lili.modules.system.service.MaollarTierService;
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

                // 1. 获取全局销售额等级 (USD) 用于确定汇率
                double totalSalesCNY = orderService.getCompletedTotalSales();
                double totalSalesUSD = maollarTierService.convertToUSD(totalSalesCNY, "CNY");
                double rate = maollarTierService.getCurrentRate(totalSalesUSD);

                // 2. 将当前订单金额转为 USD
                double orderAmountUSD = maollarTierService.convertToUSD(order.getFlowPrice(), "CNY");

                // 3. 计算本单应计基金拨备金 (10%)
                double fundReserve = maollarTierService.calculateFund(orderAmountUSD);

                // 4. 计算赠送喵币 (金额 * 汇率 * 精度)
                // 逻辑：USD * (Issuance/DeltaGMV) * 10^6
                long point = (long) (orderAmountUSD * rate * MEOW_COIN_SCALE);

                if (point > 0) {
                    memberService.updateMemberPoint(point, PointTypeEnum.INCREASE.name(), order.getMemberId(),
                            "喵领计划：消费赠送喵币 " + formatPoint(point), "REWARD_MEOW_" + order.getSn(), java.math.BigDecimal.valueOf(fundReserve));
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
            // Instead of calculating based on current rate, we calculate based on the refund ratio of the original issued points
            Order order = orderService.getBySn(afterSale.getOrderSn());
            if (order == null) return;

            // Fetch original issuance from history to get exact count
            // Note: bizId was saved with REWARD_MEOW_ prefix in orderChange
            MemberPointsHistory originalHistory = memberPointsHistoryService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberPointsHistory>()
                    .eq(MemberPointsHistory::getMemberId, afterSale.getMemberId())
                    .eq(MemberPointsHistory::getBizId, "REWARD_MEOW_" + order.getSn())
                    .eq(MemberPointsHistory::getPointType, PointTypeEnum.INCREASE.name())
                    .like(MemberPointsHistory::getContent, "喵领计划：消费赠送喵币"));


            long pointToReduce;
            java.math.BigDecimal fundToReduce;

            if (originalHistory != null && originalHistory.getVariablePoint() > 0) {
                // 按退款金额比例扣回
                double refundRatio = afterSale.getActualRefundPrice() / order.getFlowPrice();
                pointToReduce = (long) (originalHistory.getVariablePoint() * refundRatio);
                fundToReduce = originalHistory.getFundReserve().multiply(java.math.BigDecimal.valueOf(refundRatio));
            } else {
                // Fallback (should not happen if system is consistent)
                double refundUSD = maollarTierService.convertToUSD(afterSale.getActualRefundPrice(), "CNY");
                double rate = maollarTierService.getCurrentRate(refundUSD);
                pointToReduce = (long) (refundUSD * rate * MEOW_COIN_SCALE);
                fundToReduce = java.math.BigDecimal.valueOf(maollarTierService.calculateFund(refundUSD));
            }

            memberService.updateMemberPoint(pointToReduce, PointTypeEnum.REDUCE.name(), afterSale.getMemberId(),
                    "售后完成，回退消费赠送喵币 " + formatPoint(pointToReduce), "RETURN_REFUND_" + afterSale.getSn(), fundToReduce.negate());


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
