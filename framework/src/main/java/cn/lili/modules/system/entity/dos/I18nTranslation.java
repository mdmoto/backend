package cn.lili.modules.system.entity.dos;

import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * 多语言翻译实体
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("li_i18n_translation")
@ApiModel(value = "多语言翻译")
public class I18nTranslation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "翻译key不能为空")
    @ApiModelProperty(value = "翻译key，如 point.order.reward")
    private String translationKey;

    @NotBlank(message = "模块不能为空")
    @ApiModelProperty(value = "模块：POINT, ORDER, GOODS, WALLET, SYSTEM")
    private String module;

    @ApiModelProperty(value = "简体中文")
    private String zhCn;

    @ApiModelProperty(value = "English")
    private String enUs;

    @ApiModelProperty(value = "日本語")
    private String jaJp;

    @ApiModelProperty(value = "한국어")
    private String koKr;

    @ApiModelProperty(value = "ภาษาไทย")
    private String thTh;

    @ApiModelProperty(value = "Español")
    private String esEs;

    @ApiModelProperty(value = "Français")
    private String frFr;

    @ApiModelProperty(value = "Tiếng Việt")
    private String viVn;

    @ApiModelProperty(value = "翻译说明")
    private String description;

    @ApiModelProperty(value = "是否系统预置（true=系统，false=用户自定义）")
    private Boolean isSystem;

    @ApiModelProperty(value = "删除标志")
    private Boolean deleteFlag;
}

