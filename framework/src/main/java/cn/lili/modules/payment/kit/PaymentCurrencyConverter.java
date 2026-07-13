package cn.lili.modules.payment.kit;

import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.modules.system.service.FxRateSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts the platform's source amount into a payment provider currency using
 * the persisted USD-based FX snapshot. Payment creation must fail closed when
 * a rate is unavailable; charging the unconverted numeric value is unsafe.
 */
@Component
public class PaymentCurrencyConverter {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = new HashSet<>(Arrays.asList(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF",
            "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    ));

    @Autowired
    private FxRateSnapshotService fxRateSnapshotService;

    public BigDecimal convert(BigDecimal sourceAmount, String sourceCurrency, String targetCurrency) {
        String source = normalize(sourceCurrency);
        String target = normalize(targetCurrency);
        if (sourceAmount == null || sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        if (source.equals(target)) {
            return sourceAmount.setScale(decimalPlaces(target), RoundingMode.HALF_UP);
        }

        Map<String, Double> rates = fxRateSnapshotService.getRates("USD");
        BigDecimal sourceRate = rate(rates, source);
        BigDecimal targetRate = rate(rates, target);
        if (sourceRate == null || targetRate == null
                || sourceRate.compareTo(BigDecimal.ZERO) <= 0
                || targetRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }

        return sourceAmount
                .divide(sourceRate, 12, RoundingMode.HALF_UP)
                .multiply(targetRate)
                .setScale(decimalPlaces(target), RoundingMode.HALF_UP);
    }

    public long toMinorUnits(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        return amount.movePointRight(decimalPlaces(normalize(currency)))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public BigDecimal fromMinorUnits(Long amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount, decimalPlaces(normalize(currency)));
    }

    private BigDecimal rate(Map<String, Double> rates, String currency) {
        if ("USD".equals(currency)) {
            return BigDecimal.ONE;
        }
        if (rates == null || rates.get(currency) == null) {
            return null;
        }
        return BigDecimal.valueOf(rates.get(currency));
    }

    private int decimalPlaces(String currency) {
        return ZERO_DECIMAL_CURRENCIES.contains(currency) ? 0 : 2;
    }

    private String normalize(String currency) {
        if (CharSequenceUtil.isBlank(currency)) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
