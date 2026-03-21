package cn.lili.modules.logistics.calculation;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class LogisticsEstimateSkuLine implements Serializable {

    @ApiModelProperty("SKU/商品ID（可选）")
    private String skuId;

    @ApiModelProperty("商品名（可选）")
    private String name;

    @ApiModelProperty("数量")
    private Integer quantity;

    @ApiModelProperty("单件重量（kg，可选）")
    private Double weightKg;
}

