package cn.lili.controller.ai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI Agent Optimized Product DTO
 * Strictly compliant with Web2 Fiat payment standards.
 */
@Data
@ApiModel(description = "AI Agent optimized product information (Fiat-centric)")
public class AiProductDTO {

    @ApiModelProperty(value = "Product Name")
    private String name;

    @ApiModelProperty(value = "Fiat Price (USD)")
    private BigDecimal priceUsd;

    @ApiModelProperty(value = "Estimated Cashback Points (MAO tokens to be distributed post-purchase)")
    private BigDecimal estimatedRewardPoints;

    @ApiModelProperty(value = "Estimated Rebate Value (USD)")
    private BigDecimal estimatedRebateValueUsd;

    @ApiModelProperty(value = "Shopping Rebate Rate (Description of value back)")
    private String rebateDescription;

    @ApiModelProperty(value = "Inventory Status")
    private String stockStatus;

    @ApiModelProperty(value = "Secure Checkout Link (Supports Visa/Mastercard/Alipay)")
    private String purchaseLink;
}
