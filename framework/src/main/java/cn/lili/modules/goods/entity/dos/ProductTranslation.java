package cn.lili.modules.goods.entity.dos;

import cn.lili.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 商品多语言翻译
 *
 * @author MaoMall
 * @since 2026-03-03
 */
@Data
@TableName("li_product_translations")
@ApiModel(value = "商品多语言翻译", description = "商品多语言翻译")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductTranslation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "SPU ID (reference to li_goods.id)")
    private String spuId;

    @ApiModelProperty(value = "Language Code (en, zh, ja, ko, ar, es, fr, th, de)")
    private String languageCode;

    @ApiModelProperty(value = "Translated Product Title")
    private String title;

    @ApiModelProperty(value = "Translated Product Description/Intro")
    private String description;

    @ApiModelProperty(value = "Translated Specifications/Parameters (JSON format)")
    private String specifications;

    public ProductTranslation(String spuId, String languageCode, String title, String description) {
        this.spuId = spuId;
        this.languageCode = languageCode;
        this.title = title;
        this.description = description;
    }
}
