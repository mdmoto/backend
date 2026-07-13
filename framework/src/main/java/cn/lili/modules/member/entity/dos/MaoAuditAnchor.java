package cn.lili.modules.member.entity.dos;

import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MAO 猫币账本公开审计锚
 */
@Data
@TableName("li_mao_audit_anchor")
@ApiModel(value = "MAO 猫币账本公开审计锚")
@EqualsAndHashCode(callSuper = true)
public class MaoAuditAnchor extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "锚定批次")
    private String anchorKey;

    @ApiModelProperty(value = "锚定日期 UTC yyyy-MM-dd")
    private String anchorDate;

    @ApiModelProperty(value = "锚定时区")
    private String anchorTimezone;

    @ApiModelProperty(value = "账本统计截止时间")
    private Date anchoredUntil;

    @ApiModelProperty(value = "Root 覆盖范围")
    private String rootScope;

    @ApiModelProperty(value = "账本 Merkle Root")
    private String merkleRoot;

    @ApiModelProperty(value = "语义账本 Hash")
    private String ledgerHash;

    @ApiModelProperty(value = "公开锚定 Payload Hash")
    private String anchorPayloadHash;

    @ApiModelProperty(value = "纳入记录数")
    private Long recordCount;

    @ApiModelProperty(value = "累计增加喵币")
    private Long totalIncreasePoints;

    @ApiModelProperty(value = "累计扣减喵币")
    private Long totalReducePoints;

    @ApiModelProperty(value = "累计基金拨备 USD")
    private BigDecimal totalFundReserve;

    @ApiModelProperty(value = "首条积分流水 ID")
    private String firstHistoryId;

    @ApiModelProperty(value = "末条积分流水 ID")
    private String lastHistoryId;

    @ApiModelProperty(value = "锚定状态")
    private String anchorStatus;

    @ApiModelProperty(value = "外部锚定目标")
    private String anchorTarget;

    @ApiModelProperty(value = "外部锚定交易/对象 Hash")
    private String anchorTxHash;

    @ApiModelProperty(value = "外部锚定失败原因")
    private String errorLog;
}
