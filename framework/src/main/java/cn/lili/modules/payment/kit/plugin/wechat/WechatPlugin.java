package cn.lili.modules.payment.kit.plugin.wechat;

import cn.hutool.json.JSONUtil;
import cn.lili.cache.Cache;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.utils.CurrencyUtil;
import cn.lili.common.utils.SnowFlake;
import cn.lili.common.utils.StringUtils;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.connect.entity.Connect;
import cn.lili.modules.connect.entity.enums.SourceEnum;
import cn.lili.modules.connect.service.ConnectService;
import cn.lili.modules.member.entity.dto.ConnectQueryDTO;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.payment.entity.RefundLog;
import cn.lili.modules.payment.entity.enums.PaymentMethodEnum;
import cn.lili.modules.payment.kit.CashierSupport;
import cn.lili.modules.payment.kit.Payment;
import cn.lili.modules.payment.kit.core.enums.SignType;
import cn.lili.modules.payment.kit.core.kit.HttpKit;
import cn.lili.modules.payment.kit.core.kit.IpKit;
import cn.lili.modules.payment.kit.core.kit.WxPayKit;
import cn.lili.modules.payment.kit.core.utils.DateTimeZoneUtil;
import cn.lili.modules.payment.kit.dto.PayParam;
import cn.lili.modules.payment.kit.dto.PaymentSuccessParams;
import cn.lili.modules.payment.kit.params.dto.CashierParam;
import cn.lili.modules.payment.service.PaymentService;
import cn.lili.modules.payment.service.RefundLogService;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.WithdrawalSetting;
import cn.lili.modules.system.entity.dto.connect.WechatConnectSetting;
import cn.lili.modules.system.entity.dto.connect.dto.WechatConnectSettingItem;
import cn.lili.modules.system.entity.dto.payment.WechatPaymentSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import cn.lili.modules.wallet.entity.dos.MemberWithdrawApply;
import cn.lili.modules.wallet.entity.dto.TransferResultDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.gson.Gson;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.app.AppService;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.h5.model.SceneInfo;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.transferbatch.TransferBatchService;
import com.wechat.pay.java.service.transferbatch.model.InitiateBatchTransferRequest;
import com.wechat.pay.java.service.transferbatch.model.InitiateBatchTransferResponse;
import com.wechat.pay.java.service.transferbatch.model.TransferDetailInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 微信支付 - Patched for Debugging (Unified Build)
 */
@Component
public class WechatPlugin implements Payment {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WechatPlugin.class);

    @Autowired
    private CashierSupport cashierSupport;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private Cache<String> cache;
    @Autowired
    private RefundLogService refundLogService;
    @Autowired
    private SettingService settingService;
    @Autowired
    private ConnectService connectService;
    @Autowired
    private OrderService orderService;

    @Override
    public ResultMessage<Object> h5pay(HttpServletRequest request, HttpServletResponse response1, PayParam payParam) {
        try {
            log.info("WechatPlugin DEBUG: Starting h5pay flow for order {}", payParam.getSn());
            CashierParam cashierParam = cashierSupport.cashierParam(payParam);
            SceneInfo sceneInfo = new SceneInfo();
            sceneInfo.setPayerClientIp(IpKit.getRealIp(request));
            H5Info h5Info = new H5Info();
            h5Info.setType("WAP");
            sceneInfo.setH5Info(h5Info);

            Integer fen = CurrencyUtil.fen(cashierParam.getPrice());
            String outOrderNo = SnowFlake.getIdStr();
            String timeExpire = DateTimeZoneUtil.dateToTimeZone(System.currentTimeMillis() + 1000 * 60 * 3);
            String attach = java.net.URLEncoder.encode(JSONUtil.toJsonStr(payParam), StandardCharsets.UTF_8.name());

            WechatPaymentSetting setting = wechatPaymentSetting();
            String appid = setting.getH5AppId();
            if (appid == null) {
                log.error("WechatPlugin DEBUG: H5 AppID is NULL!");
                throw new ServiceException(ResultCode.WECHAT_PAYMENT_NOT_SETTING);
            }

            Config config = null;
            if ("CERT".equals(setting.getPublicType())) {
                log.info("WechatPlugin DEBUG: Using CERT mode");
                config = this.getCertificateConfig(setting);
            } else {
                log.info("WechatPlugin DEBUG: Using PUBLIC KEY mode");
                config = this.getPublicKeyConfig(setting);
            }
            log.info("WechatPlugin DEBUG: Config created successfully");

            H5Service service = new H5Service.Builder().config(config).build();

            com.wechat.pay.java.service.payments.h5.model.PrepayRequest prepayRequest = new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
            com.wechat.pay.java.service.payments.h5.model.Amount amount = new com.wechat.pay.java.service.payments.h5.model.Amount();
            amount.setTotal(fen);
            prepayRequest.setAmount(amount);
            prepayRequest.setAppid(appid);
            prepayRequest.setMchid(setting.getMchId());
            prepayRequest.setDescription(cashierParam.getDetail());
            prepayRequest.setNotifyUrl(notifyUrl(wechatPaymentSetting().getCallbackUrl(), PaymentMethodEnum.WECHAT));
            prepayRequest.setAttach(attach);
            prepayRequest.setTimeExpire(timeExpire);
            prepayRequest.setOutTradeNo(outOrderNo);
            prepayRequest.setSceneInfo(sceneInfo);

            log.info("WechatPlugin DEBUG: Calling prepay... AppID: {}", appid);
            com.wechat.pay.java.service.payments.h5.model.PrepayResponse response = service.prepay(prepayRequest);
            log.info("WechatPlugin DEBUG: Prepay successful, url: {}", response.getH5Url());

            updateOrderPayNo(payParam, outOrderNo);

            return ResultUtil.data(response.getH5Url());
        } catch (Exception e) {
            log.error("微信H5支付错误 (PATCHED DEBUG): " + e.getMessage(), e);
            e.printStackTrace(); // Print full stack to log
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    @Override
    public ResultMessage<Object> jsApiPay(HttpServletRequest request, PayParam payParam) {
        return null;
    }

    @Override
    public ResultMessage<Object> appPay(HttpServletRequest request, PayParam payParam) {
        return null;
    }

    @Override
    public ResultMessage<Object> nativePay(HttpServletRequest request, PayParam payParam) {
        return null;
    }

    @Override
    public ResultMessage<Object> mpPay(HttpServletRequest request, PayParam payParam) {
        return null;
    }

    @Override
    public void callBack(HttpServletRequest request) {
    }

    @Override
    public void notify(HttpServletRequest request) {
    }

    @Override
    public TransferResultDTO transfer(MemberWithdrawApply memberWithdrawApply) {
        return null;
    }

    @Override
    public void refund(RefundLog refundLog) {
    }

    @Override
    public void refundNotify(HttpServletRequest request) {
    }

    private WechatPaymentSetting wechatPaymentSetting() {
        Setting systemSetting = settingService.get(SettingEnum.WECHAT_PAYMENT.name());
        if (systemSetting == null || systemSetting.getSettingValue() == null
                || systemSetting.getSettingValue().trim().isEmpty()) {
            throw new ServiceException(ResultCode.WECHAT_PAYMENT_NOT_SETTING);
        }
        return JSONUtil.toBean(systemSetting.getSettingValue(), WechatPaymentSetting.class);
    }

    private RSAPublicKeyConfig getPublicKeyConfig(WechatPaymentSetting setting) {
        return new RSAPublicKeyConfig.Builder()
                .merchantId(setting.getMchId())
                .privateKey(setting.getApiclientKey())
                .publicKey(setting.getPublicKey())
                .publicKeyId(setting.getPublicId())
                .merchantSerialNumber(setting.getSerialNumber())
                .apiV3Key(setting.getApiKey3())
                .build();
    }

    private RSAAutoCertificateConfig getCertificateConfig(WechatPaymentSetting setting) {
        try {
            log.info("DEBUG CONFIG - MchId: {}", setting.getMchId());
            log.info("DEBUG CONFIG - Serial: {}", setting.getSerialNumber());
            log.info("DEBUG CONFIG - ApiKey3: {}", setting.getApiKey3() != null ? "MASKED" : "NULL");
            log.info("DEBUG CONFIG - PrivateKey Len: {}",
                    setting.getApiclientKey() != null ? setting.getApiclientKey().length() : -1);

            return new RSAAutoCertificateConfig.Builder()
                    .merchantId(setting.getMchId())
                    .privateKey(setting.getApiclientKey())
                    .merchantSerialNumber(setting.getSerialNumber())
                    .apiV3Key(setting.getApiKey3())
                    .build();
        } catch (Exception e) {
            log.error("FATAL ERROR in getCertificateConfig: " + e.getMessage(), e);
            throw e;
        }
    }

    private void updateOrderPayNo(PayParam payParam, String outOrderNo) {
        if ("ORDER".equals(payParam.getOrderType())) {
            orderService.update(new LambdaUpdateWrapper<Order>()
                    .eq(Order::getSn, payParam.getSn())
                    .set(Order::getPayOrderNo, outOrderNo));
        } else if ("TRADE".equals(payParam.getOrderType())) {
            orderService.update(new LambdaUpdateWrapper<Order>()
                    .eq(Order::getTradeSn, payParam.getSn())
                    .set(Order::getPayOrderNo, outOrderNo));
        }
    }
}
