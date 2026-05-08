package cn.lili.modules.openapi.entity;

import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@TableName("li_open_api_key")
@ApiModel(value = "Open API 密钥表")
public class OpenApiKey extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "关联会员ID")
    private String memberId;

    @ApiModelProperty(value = "API Key")
    private String apiKey;

    @ApiModelProperty(value = "API Secret")
    private String apiSecret;

    @ApiModelProperty(value = "权限范围")
    private String permissions;

    @ApiModelProperty(value = "状态 OPEN,CLOSE")
    private String status;
}
