package cn.lili.event.impl;


import cn.lili.modules.member.entity.dos.MemberPointsHistory;
import cn.lili.modules.member.entity.enums.PointTypeEnum;
import cn.lili.modules.member.service.MemberPointsHistoryService;
import cn.lili.modules.member.service.MemberService;
import cn.lili.modules.order.aftersale.entity.dos.AfterSale;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.dto.OrderMessage;
import cn.lili.modules.order.order.entity.enums.OrderStatusEnum;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.system.service.MaollarTierService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaocoinEconomicModelTest {

    @Mock
    private MemberService memberService;

    @Mock
    private MemberPointsHistoryService memberPointsHistoryService;

    @Mock
    private OrderService orderService;

    @Mock
    private MaollarTierService maollarTierService;

    @InjectMocks
    private MemberPointExecute memberPointExecute;

    @Test
    void testOrderCompletionReward() {
        String orderSn = "TEST_ORDER_001";
        String memberId = "MEMBER_001";
        
        Order order = new Order();
        order.setSn(orderSn);
        order.setMemberId(memberId);
        order.setFlowPrice(100.0);
        
        when(orderService.getBySn(orderSn)).thenReturn(order);
        when(maollarTierService.convertToUSD(anyDouble(), anyString())).thenReturn(14.0);
        when(maollarTierService.getCurrentRate(anyDouble())).thenReturn(10.0);
        when(maollarTierService.calculateFund(anyDouble())).thenReturn(1.4);

        OrderMessage message = new OrderMessage();
        message.setOrderSn(orderSn);
        message.setNewStatus(OrderStatusEnum.COMPLETED);
        
        memberPointExecute.orderChange(message);
        
        // 14 * 10 * 1,000,000 = 140,000,000
        verify(memberService, times(1)).updateMemberPoint(
                eq(140000000L), eq(PointTypeEnum.INCREASE.name()), eq(memberId), 
                anyString(), eq("REWARD_MEOW_" + orderSn), any(BigDecimal.class)
        );
    }

    @Test
    void testPartialRefundProportionalDeduction() {
        String orderSn = "TEST_ORDER_002";
        String memberId = "MEMBER_002";
        String afterSaleSn = "AS_001";
        
        AfterSale afterSale = new AfterSale();
        afterSale.setSn(afterSaleSn);
        afterSale.setOrderSn(orderSn);
        afterSale.setMemberId(memberId);
        afterSale.setActualRefundPrice(50.0); 
        afterSale.setServiceStatus(cn.lili.modules.order.trade.entity.enums.AfterSaleStatusEnum.COMPLETE.name());

        Order order = new Order();
        order.setSn(orderSn);
        order.setFlowPrice(100.0);
        when(orderService.getBySn(orderSn)).thenReturn(order);

        MemberPointsHistory originalHistory = new MemberPointsHistory();
        originalHistory.setVariablePoint(1000L);
        originalHistory.setFundReserve(new BigDecimal("10.0")); 
        
        when(memberPointsHistoryService.getOne(any())).thenReturn(originalHistory);
        
        memberPointExecute.afterSaleStatusChange(afterSale);
        
        // Verify deduction: (50/100) * 1000 = 500
        verify(memberService).updateMemberPoint(
                eq(500L), eq(PointTypeEnum.REDUCE.name()), eq(memberId), 
                anyString(), eq("RETURN_REFUND_" + afterSaleSn), any(BigDecimal.class)
        );
    }
}
