package cn.lili.modules.openapi.entity.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class OpenApiKeyVO {

    @ApiModelProperty(value = "关联会员ID")
    private String memberId;

    @ApiModelProperty(value = "API Key")
    private String apiKey;

    @ApiModelProperty(value = "权限范围")
    private String permissions;

    @ApiModelProperty(value = "状态 OPEN,CLOSE")
    private String status;
}
