package cn.lili.modules.system.entity.dto.payment;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 泰国 Thai QR / PromptPay 支付设置。
 */
@Data
@Accessors(chain = true)
public class ThaiQrPaymentSetting {

    /**
     * 是否启用。
     */
    private Boolean enabled = false;

    /**
     * Thai QR 静态码内容，前端可据此生成二维码。
     */
    private String qrPayload;

    /**
     * 已生成的二维码图片地址。
     */
    private String qrCodeUrl;

    /**
     * 收款方展示名称。
     */
    private String merchantName;

    /**
     * 本地收单/银行服务商名称。
     */
    private String providerName = "Thai QR";

    /**
     * 展示币种。
     */
    private String currency = "THB";

    /**
     * 收单主体。
     */
    private String merchantEntity = "TH_COMPANY";

    /**
     * 收单账号标识。
     */
    private String acquirerAccount = "thai_bank_qr";

    /**
     * 结算账户标识。
     */
    private String settlementEntity = "TH_BANK";

    /**
     * 客户侧提示语。
     */
    private String instructions = "Please scan the Thai QR / PromptPay code and include the order number as reference.";
}
