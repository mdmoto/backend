package cn.lili.modules.member.entity.dos;

import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * 积分类型配置实体
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("li_point_type_config")
@ApiModel(value = "积分类型配置")
public class PointTypeConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "类型代码不能为空")
    @ApiModelProperty(value = "类型代码，如 ORDER_REWARD, SIGNIN_REWARD")
    private String typeCode;

    @NotBlank(message = "名称翻译key不能为空")
    @ApiModelProperty(value = "名称翻译key")
    private String nameI18nKey;

    @ApiModelProperty(value = "描述翻译key")
    private String descriptionI18nKey;

    @ApiModelProperty(value = "默认积分数（0表示动态计算）")
    private Integer pointAmount;

    @ApiModelProperty(value = "是否启用")
    private Boolean isActive;

    @ApiModelProperty(value = "排序")
    private Integer sortOrder;

    @ApiModelProperty(value = "图标URL")
    private String icon;
}

