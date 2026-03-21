package cn.lili.modules.logistics.calculation;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class LogisticsQuote implements Serializable {

    @ApiModelProperty("渠道编码（如 4PX 产品代码/自定义代码）")
    private String serviceCode;

    @ApiModelProperty("渠道名称（展示用）")
    private String serviceName;

    @ApiModelProperty("币种（如 USD/JPY/CNY）")
    private String currency;

    @ApiModelProperty("预估运费")
    private Double amount;

    @ApiModelProperty("预估时效（天，可选）")
    private Integer estimatedDays;

    @ApiModelProperty("附加说明（可选）")
    private String note;
}

