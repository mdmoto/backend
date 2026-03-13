package cn.lili.modules.payment.entity;

import cn.lili.mybatis.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Stripe 支付快照
 * 用于基于真实收款发放猫币 (Maocoin)
 */
@Data
@TableName("li_stripe_payment_snapshot")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "Stripe 支付快照")
public class StripePaymentSnapshot extends BaseIdEntity {

    @ApiModelProperty(value = "订单编号")
    private String orderSn;

    @ApiModelProperty(value = "Stripe Charge ID")
    private String chargeId;

    @ApiModelProperty(value = "Stripe Payment Intent ID")
    private String paymentIntentId;

    @ApiModelProperty(value = "Stripe Balance Transaction ID")
    private String balanceTransactionId;

    @ApiModelProperty(value = "真实净收款 (USD)")
    private BigDecimal amountNetUsd;

    @ApiModelProperty(value = "真实总收款 (USD)")
    private BigDecimal amountGrossUsd;

    @ApiModelProperty(value = "Stripe 手续费 (USD)")
    private BigDecimal feeUsd;

    @ApiModelProperty(value = "币种")
    private String currency;

    @ApiModelProperty(value = "支付状态: UNCONFIRMED, CONFIRMED, REFUNDED")
    private String paymentStatus;

    @ApiModelProperty(value = "Stripe 原始数据快照 (JSON)")
    private String rawData;

    @CreatedDate
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间", hidden = true)
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间", hidden = true)
    private Date updateTime;
}
