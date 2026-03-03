package cn.lili.modules.member.entity.dos;

import cn.lili.modules.member.entity.enums.MaoIssueStatusEnum;
import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * $MAO 提现记录
 */
@Data
@TableName("li_mao_withdrawal_log")
@ApiModel(value = "$MAO 提现记录")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MaoWithdrawalLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "会员ID")
    private String memberId;

    @ApiModelProperty(value = "提现扣除的喵币数量")
    private Long points;

    @ApiModelProperty(value = "实际发放的 $MAO 数量")
    private BigDecimal maoIssuedAmount;

    @ApiModelProperty(value = "接收地址 (Solana)")
    private String solanaWalletAddress;

    @ApiModelProperty(value = "状态")
    private String maoIssueStatus = MaoIssueStatusEnum.NONE.name();

    @ApiModelProperty(value = "Solana 交易哈希")
    private String maoTxHash;

    @ApiModelProperty(value = "错误日志")
    private String errorLog;
}
