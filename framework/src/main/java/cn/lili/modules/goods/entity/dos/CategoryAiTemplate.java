package cn.lili.modules.goods.entity.dos;

import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分类AI参数模版
 */
@Data
@TableName("li_category_ai_template")
@ApiModel(value = "分类AI参数模版")
@NoArgsConstructor
public class CategoryAiTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "绑定的分类ID")
    private Long categoryId;

    @ApiModelProperty(value = "分类英文slug")
    private String categorySlug;

    @ApiModelProperty(value = "模版版本号")
    private Integer templateVersion;

    @ApiModelProperty(value = "JSON Schema定义")
    private String schemaJson;

    @ApiModelProperty(value = "给AI的购买决策提示词")
    private String aiHint;

    @ApiModelProperty(value = "AI对比时核心维度")
    private String comparisonKeys;

    @ApiModelProperty(value = "参数单位映射")
    private String unitHints;

    @ApiModelProperty(value = "是否启用")
    private Boolean isActive;
}
