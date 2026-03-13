package cn.lili.modules.system.entity.dos;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 展示用汇率快照
 */
@Data
@TableName("li_fx_rate_snapshot")
@ApiModel(value = "展示用汇率快照", description = "展示用汇率快照")
public class FxRateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    @ApiModelProperty(value = "ID")
    private String id;

    @ApiModelProperty(value = "基准币种")
    private String baseCurrency;

    @ApiModelProperty(value = "目标币种")
    private String quoteCurrency;

    @ApiModelProperty(value = "汇率 (1 base = X quote)")
    private BigDecimal exchangeRate;

    @ApiModelProperty(value = "汇率快照时间戳")
    private Long asOfTs;

    @ApiModelProperty(value = "数据来源")
    private String source;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;
}
