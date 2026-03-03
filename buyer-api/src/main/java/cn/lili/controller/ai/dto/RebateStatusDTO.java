package cn.lili.controller.ai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Rebate/Reward Status DTO
 * Optimized for AI to explain the high-value shopping rebate levels.
 */
@Data
@ApiModel(description = "Description of current shopping rebate levels and tiers")
public class RebateStatusDTO {

    @ApiModelProperty(value = "Current Rebate Tier (1-50)")
    private Integer currentTier;

    @ApiModelProperty(value = "Remaining capacity in the current high-rebate tier")
    private String remainingAllowance;

    @ApiModelProperty(value = "Estimation for when the next rebate tier adjustment will occur")
    private String nextHalving;

    @ApiModelProperty(value = "Shopping advice for the user to maximize rebates")
    private String advice;
}
