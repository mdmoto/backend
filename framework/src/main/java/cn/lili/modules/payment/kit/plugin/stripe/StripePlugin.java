package cn.lili.modules.payment.kit.plugin.stripe;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.utils.CurrencyUtil;
import cn.lili.common.utils.SnowFlake;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.entity.enums.PaymentMethodEnum;
import cn.lili.modules.payment.kit.CashierSupport;
import cn.lili.modules.payment.kit.Payment;
import cn.lili.modules.payment.kit.dto.PayParam;
import cn.lili.modules.payment.kit.dto.PaymentSuccessParams;
import cn.lili.modules.payment.kit.params.dto.CashierParam;
import cn.lili.modules.payment.service.PaymentService;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.payment.StripePaymentSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stripe Checkout 收单插件。
 */
@Slf4j
@Component
public class StripePlugin implements Payment {

    private static final String STRIPE_CHECKOUT_SESSION_URL = "https://api.stripe.com/v1/checkout/sessions";
    private static final long WEBHOOK_TOLERANCE_SECONDS = 300L;
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = new LinkedHashSet<>(Arrays.asList(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF",
            "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    ));

    @Autowired
    private SettingService settingService;

    @Autowired
    private CashierSupport cashierSupport;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StripePaymentSnapshotService stripePaymentSnapshotService;

    @Override
    public ResultMessage<Object> h5pay(HttpServletRequest request, HttpServletResponse response, PayParam payParam) {
        return createCheckoutSession(payParam);
    }

    @Override
    public ResultMessage<Object> nativePay(HttpServletRequest request, PayParam payParam) {
        return createCheckoutSession(payParam);
    }

    @Override
    public ResultMessage<Object> appPay(HttpServletRequest request, PayParam payParam) {
        return createCheckoutSession(payParam);
    }

    @Override
    public ResultMessage<Object> jsApiPay(HttpServletRequest request, PayParam payParam) {
        return createCheckoutSession(payParam);
    }

    @Override
    public ResultMessage<Object> mpPay(HttpServletRequest request, PayParam payParam) {
        return createCheckoutSession(payParam);
    }

    @Override
    public void notify(HttpServletRequest request) {
        handleWebhook(request);
    }

    @Override
    public void callBack(HttpServletRequest request) {
        handleWebhook(request);
    }

    private ResultMessage<Object> createCheckoutSession(PayParam payParam) {
        StripePaymentSetting setting = stripePaymentSetting();
        CashierParam cashierParam = cashierSupport.cashierParam(payParam);
        String currency = normalizedCurrency(setting.getCurrency(), "THB");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("mode", "payment");
        params.put("success_url", setting.getSuccessUrl());
        params.put("cancel_url", setting.getCancelUrl());
        params.put("line_items[0][quantity]", "1");
        params.put("line_items[0][price_data][currency]", currency.toLowerCase(Locale.ROOT));
        params.put("line_items[0][price_data][unit_amount]", String.valueOf(toStripeAmount(cashierParam.getPrice(), currency)));
        params.put("line_items[0][price_data][product_data][name]", cashierParam.getDetail());
        params.put("metadata[order_sn]", payParam.getSn());
        params.put("metadata[order_type]", payParam.getOrderType());
        params.put("metadata[client_type]", payParam.getClientType());
        params.put("metadata[merchant_entity]", defaultString(setting.getMerchantEntity(), "TH_COMPANY"));
        params.put("metadata[acquirer_account]", defaultString(setting.getAcquirerAccount(), "stripe_th"));
        params.put("metadata[settlement_entity]", defaultString(setting.getSettlementEntity(), "TH_BANK"));

        List<String> paymentMethodTypes = paymentMethodTypes(setting);
        for (int i = 0; i < paymentMethodTypes.size(); i++) {
            params.put("payment_method_types[" + i + "]", paymentMethodTypes.get(i));
        }

        try (HttpResponse response = HttpRequest.post(STRIPE_CHECKOUT_SESSION_URL)
                .basicAuth(setting.getSecretKey(), "")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(formEncode(params))
                .execute()) {

            String body = response.body();
            if (!response.isOk()) {
                log.error("Stripe Checkout Session创建失败：状态：{}，响应：{}", response.getStatus(), body);
                throw new ServiceException(ResultCode.PAY_ERROR);
            }

            JSONObject session = JSONUtil.parseObj(body);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", "STRIPE");
            result.put("paymentUrl", session.getStr("url"));
            result.put("sessionId", session.getStr("id"));
            result.put("merchantEntity", setting.getMerchantEntity());
            result.put("acquirerAccount", setting.getAcquirerAccount());
            result.put("settlementEntity", setting.getSettlementEntity());
            result.put("currency", currency);
            result.put("amount", cashierParam.getPrice());
            result.put("paymentMethodTypes", paymentMethodTypes);

            log.info("Stripe Checkout Session创建成功：订单：{}，Session：{}，主体：{}",
                    payParam.getSn(), session.getStr("id"), setting.getMerchantEntity());
            return ResultUtil.data(result);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stripe Checkout Session创建异常：订单：{}", payParam.getSn(), e);
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    private void handleWebhook(HttpServletRequest request) {
        StripePaymentSetting setting = stripePaymentSetting();
        String payload = requestBody(request);
        String signature = request.getHeader("Stripe-Signature");

        if (!validStripeSignature(payload, signature, setting.getWebhookSecret())) {
            log.warn("Stripe Webhook签名校验失败");
            throw new ServiceException(ResultCode.PAY_ERROR);
        }

        JSONObject event = JSONUtil.parseObj(payload);
        String eventType = event.getStr("type");
        if (!"checkout.session.completed".equals(eventType) && !"payment_intent.succeeded".equals(eventType)) {
            log.info("Stripe Webhook事件暂不处理：{}", eventType);
            return;
        }

        JSONObject object = event.getJSONObject("data").getJSONObject("object");
        JSONObject metadata = object.getJSONObject("metadata");
        PayParam payParam = new PayParam();
        payParam.setSn(metadata.getStr("order_sn"));
        payParam.setOrderType(metadata.getStr("order_type"));
        payParam.setClientType(metadata.getStr("client_type"));

        if (CharSequenceUtil.isBlank(payParam.getSn()) || CharSequenceUtil.isBlank(payParam.getOrderType())) {
            log.warn("Stripe Webhook缺少订单元数据：事件：{}", event.getStr("id"));
            return;
        }

        String currency = normalizedCurrency(object.getStr("currency"), setting.getCurrency());
        Long amountTotal = eventAmount(object, eventType);
        BigDecimal amountPresentment = fromStripeAmount(amountTotal, currency);
        String receivableNo = defaultString(object.getStr("payment_intent"), object.getStr("id"));

        saveSnapshot(setting, event, object, metadata, currency, amountPresentment, receivableNo);

        PaymentSuccessParams paymentSuccessParams = new PaymentSuccessParams(
                PaymentMethodEnum.STRIPE.name(),
                receivableNo,
                amountPresentment.doubleValue(),
                payParam
        );
        paymentService.success(paymentSuccessParams);
        log.info("Stripe支付成功通知完成：订单：{}，流水：{}，金额：{} {}",
                payParam.getSn(), receivableNo, amountPresentment, currency);
    }

    private void saveSnapshot(StripePaymentSetting setting, JSONObject event, JSONObject object, JSONObject metadata,
                              String currency, BigDecimal amountPresentment, String receivableNo) {
        String orderSn = metadata.getStr("order_sn");
        StripePaymentSnapshot snapshot = stripePaymentSnapshotService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StripePaymentSnapshot>()
                        .eq(StripePaymentSnapshot::getOrderSn, orderSn),
                false
        );
        if (snapshot == null) {
            snapshot = new StripePaymentSnapshot();
            snapshot.setId(SnowFlake.getIdStr());
            snapshot.setOrderSn(orderSn);
        }

        snapshot.setStripeEventId(event.getStr("id"));
        snapshot.setStripeEventType(event.getStr("type"));
        snapshot.setPaymentIntentId(defaultString(object.getStr("payment_intent"), object.getStr("id")));
        snapshot.setChargeId(receivableNo);
        snapshot.setMerchantEntity(defaultString(metadata.getStr("merchant_entity"), setting.getMerchantEntity()));
        snapshot.setAcquirerAccount(defaultString(metadata.getStr("acquirer_account"), setting.getAcquirerAccount()));
        snapshot.setSettlementEntity(defaultString(metadata.getStr("settlement_entity"), setting.getSettlementEntity()));
        snapshot.setPresentmentCurrency(currency);
        snapshot.setAmountPresentment(amountPresentment);
        snapshot.setSettlementCurrency(defaultString(setting.getSettlementCurrency(), currency));
        snapshot.setPayerWallet(resolvePayerWallet(object));
        snapshot.setSupplierPayoutStatus("PENDING");
        snapshot.setCurrency(currency);
        snapshot.setPaymentStatus("CONFIRMED");
        snapshot.setAmountGrossUsd("USD".equals(currency) ? amountPresentment : BigDecimal.ZERO);
        snapshot.setAmountNetUsd("USD".equals(currency) ? amountPresentment : BigDecimal.ZERO);
        snapshot.setFeeUsd(BigDecimal.ZERO);
        snapshot.setRawData(JSONUtil.toJsonStr(event));
        snapshot.setRawDataHash(SecureUtil.sha256(snapshot.getRawData()));
        stripePaymentSnapshotService.saveOrUpdate(snapshot);
    }

    private StripePaymentSetting stripePaymentSetting() {
        Setting setting = settingService.get(SettingEnum.STRIPE_PAYMENT.name());
        if (setting == null || CharSequenceUtil.isBlank(setting.getSettingValue())) {
            throw new ServiceException(ResultCode.STRIPE_PAYMENT_NOT_SETTING);
        }
        StripePaymentSetting paymentSetting = JSONUtil.toBean(setting.getSettingValue(), StripePaymentSetting.class);
        if (CharSequenceUtil.isBlank(paymentSetting.getSecretKey())
                || CharSequenceUtil.isBlank(paymentSetting.getWebhookSecret())
                || CharSequenceUtil.isBlank(paymentSetting.getSuccessUrl())
                || CharSequenceUtil.isBlank(paymentSetting.getCancelUrl())) {
            throw new ServiceException(ResultCode.STRIPE_PAYMENT_NOT_SETTING);
        }
        return paymentSetting;
    }

    private List<String> paymentMethodTypes(StripePaymentSetting setting) {
        if (setting.getPaymentMethodTypes() == null || setting.getPaymentMethodTypes().isEmpty()) {
            return Arrays.asList("card", "promptpay");
        }
        return setting.getPaymentMethodTypes().stream()
                .filter(CharSequenceUtil::isNotBlank)
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private String requestBody(HttpServletRequest request) {
        try {
            return request.getReader().lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("读取Stripe Webhook请求体失败", e);
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    private boolean validStripeSignature(String payload, String signatureHeader, String webhookSecret) {
        if (CharSequenceUtil.isBlank(payload) || CharSequenceUtil.isBlank(signatureHeader)
                || CharSequenceUtil.isBlank(webhookSecret)) {
            return false;
        }

        String timestamp = null;
        Set<String> signatures = new LinkedHashSet<>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }

        long eventTime = Long.parseLong(timestamp);
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - eventTime) > WEBHOOK_TOLERANCE_SECONDS) {
            log.warn("Stripe Webhook时间戳超出容忍窗口：eventTime={}，now={}", eventTime, now);
            return false;
        }

        String signedPayload = timestamp + "." + payload;
        String expected = hmacSha256Hex(signedPayload, webhookSecret);
        return signatures.stream().anyMatch(signature ->
                MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        signature.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    private String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(defaultString(value, ""), StandardCharsets.UTF_8);
    }

    private long toStripeAmount(Double price, String currency) {
        if (ZERO_DECIMAL_CURRENCIES.contains(currency)) {
            return BigDecimal.valueOf(price).setScale(0, RoundingMode.HALF_UP).longValue();
        }
        return CurrencyUtil.getFenLong(price);
    }

    private BigDecimal fromStripeAmount(Long amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (ZERO_DECIMAL_CURRENCIES.contains(currency)) {
            return BigDecimal.valueOf(amount);
        }
        return BigDecimal.valueOf(amount, 2);
    }

    private Long eventAmount(JSONObject object, String eventType) {
        if ("checkout.session.completed".equals(eventType)) {
            return object.getLong("amount_total");
        }
        return object.getLong("amount");
    }

    private String resolvePayerWallet(JSONObject object) {
        JSONArray paymentMethodTypes = object.getJSONArray("payment_method_types");
        if (paymentMethodTypes == null || paymentMethodTypes.isEmpty()) {
            return "UNKNOWN";
        }
        String type = paymentMethodTypes.getStr(0);
        if ("wechat_pay".equals(type)) {
            return "WECHAT";
        }
        if ("alipay".equals(type)) {
            return "ALIPAY";
        }
        if ("promptpay".equals(type)) {
            return "PROMPTPAY";
        }
        if ("card".equals(type)) {
            return "CARD";
        }
        return type.toUpperCase(Locale.ROOT);
    }

    private String normalizedCurrency(String currency, String fallback) {
        return defaultString(currency, fallback).trim().toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value, String fallback) {
        return CharSequenceUtil.isBlank(value) ? fallback : value;
    }
}
