package cn.lili.modules.payment.serviceimpl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.kit.PaymentCurrencyConverter;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripePaymentReconciliationServiceImplTest {

    @Test
    void confirmsSnapshotFromExactBalanceTransactionAmounts() throws Exception {
        StripePaymentSnapshotService snapshotService = mock(StripePaymentSnapshotService.class);
        PaymentCurrencyConverter converter = mock(PaymentCurrencyConverter.class);
        when(converter.toMinorUnits(new BigDecimal("100.00"), "THB")).thenReturn(10_000L);
        when(converter.fromMinorUnits(10_000L, "THB")).thenReturn(new BigDecimal("100.00"));
        when(converter.fromMinorUnits(300L, "THB")).thenReturn(new BigDecimal("3.00"));
        when(converter.fromMinorUnits(9_700L, "THB")).thenReturn(new BigDecimal("97.00"));
        when(converter.rateToUsdForAccounting("THB")).thenReturn(new BigDecimal("0.027777777778"));
        when(converter.toUsdForAccounting(new BigDecimal("100.00"), "THB"))
                .thenReturn(new BigDecimal("2.77777778"));
        when(converter.toUsdForAccounting(new BigDecimal("3.00"), "THB"))
                .thenReturn(new BigDecimal("0.08333333"));
        when(converter.toUsdForAccounting(new BigDecimal("97.00"), "THB"))
                .thenReturn(new BigDecimal("2.69444444"));

        StripePaymentReconciliationServiceImpl service = new StripePaymentReconciliationServiceImpl();
        inject(service, "snapshotService", snapshotService);
        inject(service, "currencyConverter", converter);

        StripePaymentSnapshot snapshot = new StripePaymentSnapshot();
        snapshot.setId("snapshot-1");
        snapshot.setOrderSn("ORDER-1");
        snapshot.setPaymentIntentId("pi_1");
        snapshot.setPresentmentCurrency("THB");
        snapshot.setAmountPresentment(new BigDecimal("100.00"));
        snapshot.setRawData("{\"eventId\":\"evt_1\"}");

        JSONObject paymentIntent = JSONUtil.parseObj("{" +
                "\"id\":\"pi_1\",\"status\":\"succeeded\",\"currency\":\"thb\"," +
                "\"amount_received\":10000,\"metadata\":{\"order_sn\":\"ORDER-1\"}," +
                "\"latest_charge\":{\"id\":\"ch_1\",\"paid\":true,\"status\":\"succeeded\"," +
                "\"currency\":\"thb\",\"amount\":10000," +
                "\"balance_transaction\":{\"id\":\"txn_1\",\"type\":\"charge\"," +
                "\"source\":\"ch_1\",\"amount\":10000,\"fee\":300,\"net\":9700," +
                "\"currency\":\"thb\",\"status\":\"pending\",\"available_on\":1800000000}}}");

        StripePaymentSnapshot reconciled = service.applyStripeResponse(snapshot, paymentIntent);

        assertEquals("CONFIRMED", reconciled.getPaymentStatus());
        assertEquals("ch_1", reconciled.getChargeId());
        assertEquals("txn_1", reconciled.getBalanceTransactionId());
        assertEquals(new BigDecimal("97.00"), reconciled.getAmountNetSettlement());
        assertEquals(new BigDecimal("2.69444444"), reconciled.getAmountNetUsd());
        assertEquals("pending", reconciled.getBalanceTransactionStatus());
        assertNotNull(reconciled.getFundsAvailableAt());
        assertNotNull(reconciled.getReconciledAt());
        verify(snapshotService).updateById(snapshot);
    }

    @Test
    void rejectsBalanceTransactionWhoseNetDoesNotEqualGrossMinusFee() throws Exception {
        StripePaymentSnapshotService snapshotService = mock(StripePaymentSnapshotService.class);
        PaymentCurrencyConverter converter = mock(PaymentCurrencyConverter.class);
        when(converter.toMinorUnits(new BigDecimal("100.00"), "THB")).thenReturn(10_000L);

        StripePaymentReconciliationServiceImpl service = new StripePaymentReconciliationServiceImpl();
        inject(service, "snapshotService", snapshotService);
        inject(service, "currencyConverter", converter);

        StripePaymentSnapshot snapshot = new StripePaymentSnapshot();
        snapshot.setId("snapshot-1");
        snapshot.setOrderSn("ORDER-1");
        snapshot.setPaymentIntentId("pi_1");
        snapshot.setPresentmentCurrency("THB");
        snapshot.setAmountPresentment(new BigDecimal("100.00"));

        JSONObject paymentIntent = JSONUtil.parseObj("{" +
                "\"id\":\"pi_1\",\"status\":\"succeeded\",\"currency\":\"thb\"," +
                "\"amount_received\":10000,\"metadata\":{\"order_sn\":\"ORDER-1\"}," +
                "\"latest_charge\":{\"id\":\"ch_1\",\"paid\":true,\"status\":\"succeeded\"," +
                "\"currency\":\"thb\",\"amount\":10000," +
                "\"balance_transaction\":{\"id\":\"txn_1\",\"type\":\"charge\"," +
                "\"source\":\"ch_1\",\"amount\":10000,\"fee\":300,\"net\":9800," +
                "\"currency\":\"thb\"}}}");

        assertThrows(cn.lili.common.exception.ServiceException.class,
                () -> service.applyStripeResponse(snapshot, paymentIntent));
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
