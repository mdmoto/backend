package cn.lili.modules.pdd.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 拼多多 OAuth Token 信息.
 */
@Data
@ApiModel(value = "拼多多OAuth Token信息")
public class PddToken {

    @ApiModelProperty(value = "访问令牌", required = true)
    private String accessToken;

    @ApiModelProperty(value = "刷新令牌", required = true)
    private String refreshToken;

    @ApiModelProperty(value = "访问令牌过期时间（秒）", required = true)
    private long expiresIn;

    @ApiModelProperty(value = "刷新令牌过期时间（秒）")
    private Long refreshTokenExpiresIn;

    @ApiModelProperty(value = "授权范围")
    private String scope;

    @ApiModelProperty(value = "店铺 owner_id")
    private Long ownerId;

    @ApiModelProperty(value = "店铺 owner_name")
    private String ownerName;

    @ApiModelProperty(value = "mall_id")
    private Long mallId;

    @ApiModelProperty(value = "token 获取时间（epoch second）", required = true)
    private long fetchedAt;

    @ApiModelProperty(value = "访问令牌过期时间戳（epoch second）", required = true)
    private long expiresAt;

    @ApiModelProperty(value = "刷新令牌过期时间戳（epoch second）")
    private Long refreshTokenExpiresAt;

    @ApiModelProperty(value = "授权时透传的 state")
    private String state;
}

