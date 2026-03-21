package cn.lili.modules.logistics.calculation;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class LogisticsEstimateRequest implements Serializable {

    @ApiModelProperty("收件国家/地区 ISO 3166-1 alpha-2（如 US/JP/CN）")
    private String countryCode;

    @ApiModelProperty("收件邮编/邮政编码")
    private String postalCode;

    @ApiModelProperty("收件城市（可选）")
    private String city;

    @ApiModelProperty("收件省/州（可选）")
    private String state;

    @ApiModelProperty("总重量（kg）")
    private Double totalWeightKg;

    @ApiModelProperty("外箱尺寸（cm）：长")
    private Double lengthCm;

    @ApiModelProperty("外箱尺寸（cm）：宽")
    private Double widthCm;

    @ApiModelProperty("外箱尺寸（cm）：高")
    private Double heightCm;

    @ApiModelProperty("可选：商品明细（用于渠道规则/敏感品判断）")
    private List<LogisticsEstimateSkuLine> skuLines;
}

