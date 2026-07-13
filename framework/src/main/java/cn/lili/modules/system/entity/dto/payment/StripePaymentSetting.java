package cn.lili.modules.system.entity.dto.payment;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Stripe 支付设置。
 */
@Data
@Accessors(chain = true)
public class StripePaymentSetting {

    /**
     * Stripe Secret Key.
     */
    private String secretKey;

    /**
     * Stripe Webhook signing secret.
     */
    private String webhookSecret;

    /**
     * 支付成功后跳转地址。
     */
    private String successUrl;

    /**
     * 取消支付后跳转地址。
     */
    private String cancelUrl;

    /**
     * Cashier amount source currency. Existing Maollar order totals are CNY.
     */
    private String sourceCurrency = "CNY";

    /**
     * 展示给客户的币种。泰国主体建议 THB，新加坡主体可用 SGD/USD。
     */
    private String currency = "THB";

    /**
     * 结算币种。
     */
    private String settlementCurrency = "THB";

    /**
     * 当前收单主体。
     */
    private String merchantEntity = "TH_COMPANY";

    /**
     * 当前收单账号标识。
     */
    private String acquirerAccount = "stripe_th";

    /**
     * 当前结算主体/账户标识。
     */
    private String settlementEntity = "TH_BANK";

    /**
     * Checkout 支付方式。泰国先跑 card/promptpay；迁新加坡后可切 card/alipay/wechat_pay。
     */
    private List<String> paymentMethodTypes = new ArrayList<>();
}
