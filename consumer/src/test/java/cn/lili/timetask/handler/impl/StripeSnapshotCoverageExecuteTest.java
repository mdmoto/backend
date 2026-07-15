package cn.lili.timetask.handler.impl;

import cn.lili.modules.email.EmailUtil;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.enums.OrderStatusEnum;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.payment.entity.enums.PaymentMethodEnum;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.modules.system.service.SettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeSnapshotCoverageExecuteTest {

    @Mock
    private OrderService orderService;

    @Mock
    private StripePaymentSnapshotService stripePaymentSnapshotService;

    @Mock
    private EmailUtil emailUtil;

    @Mock
    private SettingService settingService;

    @InjectMocks
    private StripeSnapshotCoverageExecute execute;

    @Test
    void ignoresCompletedOrdersPaidByAnotherMethod() {
        Order bankTransferOrder = new Order();
        bankTransferOrder.setSn("BANK-TRANSFER-ORDER");
        bankTransferOrder.setOrderStatus(OrderStatusEnum.COMPLETED.name());
        bankTransferOrder.setPaymentMethod(PaymentMethodEnum.BANK_TRANSFER.name());
        when(orderService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(bankTransferOrder));

        execute.execute();

        verify(orderService).list(any(LambdaQueryWrapper.class));
        verifyNoInteractions(stripePaymentSnapshotService, emailUtil);
    }
}
