package cn.lili.modules.goods.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.util.Date;

/**
 * 商品AI上下文快照
 */
@Data
@TableName("li_goods_ai_context")
@ApiModel(value = "商品AI上下文快照")
@NoArgsConstructor
public class GoodsAiContext {

    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "唯一标识", hidden = true)
    private String id;

    @CreatedDate
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间", hidden = true)
    private Date createTime;

    @LastModifiedDate
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间", hidden = true)
    private Date updateTime;

    @ApiModelProperty(value = "商品ID")
    private String goodsId;

    @ApiModelProperty(value = "分类ID")
    private Long categoryId;

    @ApiModelProperty(value = "模版版本")
    private Integer templateVersion;

    @ApiModelProperty(value = "完整的AI上下文JSON")
    private String aiContext;

    @ApiModelProperty(value = "是否需要刷新")
    private Boolean needsRefresh;
}
