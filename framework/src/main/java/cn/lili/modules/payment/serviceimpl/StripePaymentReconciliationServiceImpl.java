package cn.lili.modules.payment.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.kit.PaymentCurrencyConverter;
import cn.lili.modules.payment.service.StripePaymentReconciliationService;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.modules.system.entity.dto.payment.StripePaymentSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Uses the expanded PaymentIntent -> Charge -> BalanceTransaction chain as the
 * financial source of truth. Checkout session totals alone do not include fees.
 */
@Slf4j
@Service
public class StripePaymentReconciliationServiceImpl implements StripePaymentReconciliationService {

    private static final String PAYMENT_INTENT_URL = "https://api.stripe.com/v1/payment_intents/";

    @Autowired
    private StripePaymentSnapshotService snapshotService;

    @Autowired
    private PaymentCurrencyConverter currencyConverter;

    @Override
    public StripePaymentSnapshot reconcile(StripePaymentSnapshot snapshot, StripePaymentSetting setting) {
        validateRequest(snapshot, setting);
        if (isAlreadyReconciled(snapshot)) {
            return snapshot;
        }

        String paymentIntentId = snapshot.getPaymentIntentId();
        String url = PAYMENT_INTENT_URL
                + URLEncoder.encode(paymentIntentId, StandardCharsets.UTF_8)
                + "?expand%5B%5D=latest_charge.balance_transaction";
        try (HttpResponse response = HttpRequest.get(url)
                .basicAuth(setting.getSecretKey(), "")
                .execute()) {
            if (!response.isOk()) {
                log.warn("Stripe财务对账查询失败：PaymentIntent={}，状态={}", paymentIntentId, response.getStatus());
                throw new ServiceException(ResultCode.PAY_ERROR);
            }
            return applyStripeResponse(snapshot, JSONUtil.parseObj(response.body()));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stripe财务对账异常：PaymentIntent={}", paymentIntentId, e);
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    StripePaymentSnapshot applyStripeResponse(StripePaymentSnapshot snapshot, JSONObject paymentIntent) {
        requireEquals(snapshot.getPaymentIntentId(), paymentIntent.getStr("id"));
        requireEquals("succeeded", paymentIntent.getStr("status"));

        JSONObject metadata = paymentIntent.getJSONObject("metadata");
        if (metadata == null) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        requireEquals(snapshot.getOrderSn(), metadata.getStr("order_sn"));

        String presentmentCurrency = normalize(paymentIntent.getStr("currency"));
        requireEquals(normalize(snapshot.getPresentmentCurrency()), presentmentCurrency);
        long expectedPresentmentMinor = currencyConverter.toMinorUnits(
                snapshot.getAmountPresentment(), presentmentCurrency);
        requireLong(expectedPresentmentMinor, paymentIntent.getLong("amount_received"));

        JSONObject charge = paymentIntent.getJSONObject("latest_charge");
        if (charge == null || !Boolean.TRUE.equals(charge.getBool("paid"))) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        requireEquals("succeeded", charge.getStr("status"));
        requireEquals(presentmentCurrency, normalize(charge.getStr("currency")));
        requireLong(expectedPresentmentMinor, charge.getLong("amount"));

        JSONObject balanceTransaction = charge.getJSONObject("balance_transaction");
        if (balanceTransaction == null || !"charge".equals(balanceTransaction.getStr("type"))) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        requireEquals(charge.getStr("id"), balanceTransaction.getStr("source"));

        Long grossMinor = balanceTransaction.getLong("amount");
        Long feeMinor = balanceTransaction.getLong("fee");
        Long netMinor = balanceTransaction.getLong("net");
        if (grossMinor == null || feeMinor == null || netMinor == null
                || grossMinor <= 0 || feeMinor < 0 || netMinor <= 0
                || grossMinor - feeMinor != netMinor) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }

        String settlementCurrency = normalize(balanceTransaction.getStr("currency"));
        BigDecimal grossSettlement = currencyConverter.fromMinorUnits(grossMinor, settlementCurrency);
        BigDecimal feeSettlement = currencyConverter.fromMinorUnits(feeMinor, settlementCurrency);
        BigDecimal netSettlement = currencyConverter.fromMinorUnits(netMinor, settlementCurrency);
        BigDecimal usdRate = currencyConverter.rateToUsdForAccounting(settlementCurrency);

        snapshot.setChargeId(charge.getStr("id"));
        snapshot.setBalanceTransactionId(balanceTransaction.getStr("id"));
        snapshot.setSettlementCurrency(settlementCurrency);
        snapshot.setAmountGrossSettlement(grossSettlement);
        snapshot.setFeeSettlement(feeSettlement);
        snapshot.setAmountNetSettlement(netSettlement);
        snapshot.setUsdConversionRate(usdRate);
        snapshot.setAmountGrossUsd(currencyConverter.toUsdForAccounting(grossSettlement, settlementCurrency));
        snapshot.setFeeUsd(currencyConverter.toUsdForAccounting(feeSettlement, settlementCurrency));
        snapshot.setAmountNetUsd(currencyConverter.toUsdForAccounting(netSettlement, settlementCurrency));
        snapshot.setBalanceTransactionStatus(balanceTransaction.getStr("status"));
        snapshot.setFundsAvailableAt(epochSeconds(balanceTransaction.getLong("available_on")));
        snapshot.setReconciledAt(new Date());
        snapshot.setPaymentStatus("CONFIRMED");
        snapshot.setRawData(reconciliationAudit(snapshot.getRawData(), paymentIntent, charge, balanceTransaction));
        snapshot.setRawDataHash(SecureUtil.sha256(snapshot.getRawData()));
        snapshotService.updateById(snapshot);
        return snapshot;
    }

    private void validateRequest(StripePaymentSnapshot snapshot, StripePaymentSetting setting) {
        if (snapshot == null || setting == null || CharSequenceUtil.isBlank(setting.getSecretKey())
                || CharSequenceUtil.isBlank(snapshot.getId())
                || CharSequenceUtil.isBlank(snapshot.getOrderSn())
                || CharSequenceUtil.isBlank(snapshot.getPaymentIntentId())) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    private boolean isAlreadyReconciled(StripePaymentSnapshot snapshot) {
        return "CONFIRMED".equals(snapshot.getPaymentStatus())
                && CharSequenceUtil.isNotBlank(snapshot.getChargeId())
                && CharSequenceUtil.isNotBlank(snapshot.getBalanceTransactionId())
                && snapshot.getAmountNetUsd() != null
                && snapshot.getAmountNetUsd().compareTo(BigDecimal.ZERO) > 0;
    }

    private String reconciliationAudit(String existingRawData, JSONObject paymentIntent,
                                       JSONObject charge, JSONObject balanceTransaction) {
        JSONObject audit;
        try {
            audit = CharSequenceUtil.isBlank(existingRawData)
                    ? new JSONObject()
                    : JSONUtil.parseObj(existingRawData);
        } catch (Exception ignored) {
            audit = new JSONObject();
        }
        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("paymentIntentId", paymentIntent.getStr("id"));
        settlement.put("chargeId", charge.getStr("id"));
        settlement.put("balanceTransactionId", balanceTransaction.getStr("id"));
        settlement.put("amount", balanceTransaction.getLong("amount"));
        settlement.put("fee", balanceTransaction.getLong("fee"));
        settlement.put("net", balanceTransaction.getLong("net"));
        settlement.put("currency", balanceTransaction.getStr("currency"));
        settlement.put("status", balanceTransaction.getStr("status"));
        settlement.put("availableOn", balanceTransaction.getLong("available_on"));
        audit.set("settlement", settlement);
        return audit.toString();
    }

    private Date epochSeconds(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return null;
        }
        try {
            return new Date(Math.multiplyExact(seconds, 1000L));
        } catch (ArithmeticException e) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    private String normalize(String currency) {
        if (CharSequenceUtil.isBlank(currency)) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private void requireEquals(String expected, String actual) {
        if (CharSequenceUtil.isBlank(expected) || !expected.equals(actual)) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    private void requireLong(long expected, Long actual) {
        if (actual == null || expected != actual) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }
}
