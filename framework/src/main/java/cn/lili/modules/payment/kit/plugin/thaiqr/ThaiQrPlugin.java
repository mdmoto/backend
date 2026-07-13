package cn.lili.modules.payment.kit.plugin.thaiqr;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.payment.kit.CashierSupport;
import cn.lili.modules.payment.kit.PaymentCurrencyConverter;
import cn.lili.modules.payment.kit.Payment;
import cn.lili.modules.payment.kit.dto.PayParam;
import cn.lili.modules.payment.kit.params.dto.CashierParam;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.payment.ThaiQrPaymentSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;

/**
 * Thai QR / PromptPay 收款。
 */
@Slf4j
@Component
public class ThaiQrPlugin implements Payment {

    @Autowired
    private SettingService settingService;

    @Autowired
    private CashierSupport cashierSupport;

    @Autowired
    private PaymentCurrencyConverter paymentCurrencyConverter;

    @Override
    public ResultMessage<Object> h5pay(HttpServletRequest request, HttpServletResponse response, PayParam payParam) {
        return buildQrPayment(payParam);
    }

    @Override
    public ResultMessage<Object> nativePay(HttpServletRequest request, PayParam payParam) {
        return buildQrPayment(payParam);
    }

    @Override
    public ResultMessage<Object> appPay(HttpServletRequest request, PayParam payParam) {
        return buildQrPayment(payParam);
    }

    @Override
    public ResultMessage<Object> jsApiPay(HttpServletRequest request, PayParam payParam) {
        return buildQrPayment(payParam);
    }

    @Override
    public ResultMessage<Object> mpPay(HttpServletRequest request, PayParam payParam) {
        return buildQrPayment(payParam);
    }

    private ResultMessage<Object> buildQrPayment(PayParam payParam) {
        ThaiQrPaymentSetting setting = thaiQrPaymentSetting();
        CashierParam cashierParam = cashierSupport.cashierParam(payParam);
        BigDecimal sourceAmount = BigDecimal.valueOf(cashierParam.getPrice());
        BigDecimal paymentAmount = paymentCurrencyConverter.convert(
                sourceAmount,
                setting.getSourceCurrency(),
                setting.getCurrency()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "THAI_QR");
        result.put("providerName", setting.getProviderName());
        result.put("merchantName", setting.getMerchantName());
        result.put("merchantEntity", setting.getMerchantEntity());
        result.put("acquirerAccount", setting.getAcquirerAccount());
        result.put("settlementEntity", setting.getSettlementEntity());
        result.put("amount", paymentAmount);
        result.put("currency", setting.getCurrency());
        result.put("sourceAmount", sourceAmount);
        result.put("sourceCurrency", setting.getSourceCurrency());
        result.put("referenceNo", payParam.getSn());
        result.put("orderType", payParam.getOrderType());
        result.put("qrPayload", setting.getQrPayload());
        result.put("qrCodeUrl", setting.getQrCodeUrl());
        result.put("manualConfirmRequired", true);
        result.put("instructions", setting.getInstructions());

        log.info("Thai QR支付展示：订单：{}, 金额：{}, 主体：{}",
                payParam.getSn(), paymentAmount, setting.getMerchantEntity());
        return ResultUtil.data(result);
    }

    private ThaiQrPaymentSetting thaiQrPaymentSetting() {
        Setting setting = settingService.get(SettingEnum.THAI_QR_PAYMENT.name());
        if (setting == null || CharSequenceUtil.isBlank(setting.getSettingValue())) {
            throw new ServiceException(ResultCode.THAI_QR_PAYMENT_NOT_SETTING);
        }
        ThaiQrPaymentSetting paymentSetting = JSONUtil.toBean(setting.getSettingValue(), ThaiQrPaymentSetting.class);
        if (!Boolean.TRUE.equals(paymentSetting.getEnabled())
                || (CharSequenceUtil.isBlank(paymentSetting.getQrPayload())
                && CharSequenceUtil.isBlank(paymentSetting.getQrCodeUrl()))) {
            throw new ServiceException(ResultCode.THAI_QR_PAYMENT_NOT_SETTING);
        }
        return paymentSetting;
    }
}
