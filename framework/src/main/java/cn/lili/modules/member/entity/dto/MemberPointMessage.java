package cn.lili.modules.member.entity.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 会员积分
 *
 * @author Bulbasaur
 * @since 2020/12/14 16:31
 */
@Data
public class MemberPointMessage {

    @ApiModelProperty(value = "喵币")
    private Long point;

    @ApiModelProperty(value = "是否增加喵币")
    private String type;

    @ApiModelProperty(value = "会员id")
    private String memberId;

    @ApiModelProperty(value = "基金会应拨备金 (Foundation Liability) (USD)")
    private java.math.BigDecimal fundReserve;

    @ApiModelProperty(value = "业务关联ID (订单ID/售后单ID/申请ID)")
    private String bizId;
}

