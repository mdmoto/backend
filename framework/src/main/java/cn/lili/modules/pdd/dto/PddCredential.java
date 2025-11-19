package cn.lili.modules.pdd.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 拼多多 OAuth 客户端凭证配置.
 */
@Data
@ApiModel(value = "拼多多OAuth凭证配置")
public class PddCredential {

    @ApiModelProperty(value = "拼多多应用 client_id", required = true)
    private String clientId;

    @ApiModelProperty(value = "拼多多应用 client_secret", required = true)
    private String clientSecret;

    @ApiModelProperty(value = "回调地址 redirect_uri", required = true)
    private String redirectUri;
}

