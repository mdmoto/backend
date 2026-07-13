package cn.lili.modules.payment.kit;

import cn.lili.modules.system.service.FxRateSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCurrencyConverterTest {

    private PaymentCurrencyConverter converter;

    @BeforeEach
    void setUp() {
        FxRateSnapshotService rateService = mock(FxRateSnapshotService.class);
        Map<String, Double> rates = new HashMap<>();
        rates.put("CNY", 7.2D);
        rates.put("THB", 36D);
        rates.put("JPY", 150D);
        when(rateService.getRates("USD")).thenReturn(rates);

        converter = new PaymentCurrencyConverter();
        ReflectionTestUtils.setField(converter, "fxRateSnapshotService", rateService);
    }

    @Test
    void convertsCnyCashierAmountToThb() {
        BigDecimal converted = converter.convert(new BigDecimal("72.00"), "CNY", "THB");

        assertEquals(new BigDecimal("360.00"), converted);
        assertEquals(36000L, converter.toMinorUnits(converted, "THB"));
    }

    @Test
    void handlesZeroDecimalCurrencyMinorUnits() {
        assertEquals(500L, converter.toMinorUnits(new BigDecimal("500"), "JPY"));
        assertEquals(new BigDecimal("500"), converter.fromMinorUnits(500L, "JPY"));
    }

    @Test
    void convertsSettlementAmountToEightDecimalUsdAccountingValue() {
        assertEquals(new BigDecimal("2.77777778"),
                converter.toUsdForAccounting(new BigDecimal("100.00"), "THB"));
        assertEquals(new BigDecimal("0.027777777778"), converter.rateToUsdForAccounting("THB"));
    }
}
