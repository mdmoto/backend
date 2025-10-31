package cn.lili.modules.system.entity.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 积分设置
 *
 * @author Chopper
 * @since 2020/11/17 7:59 下午
 */
@Data
public class PointSetting implements Serializable {

    private static final long serialVersionUID = -4261856614779031745L;
    @ApiModelProperty(value = "注册")
    private Integer register;

    @ApiModelProperty(value = "消费1元赠送多少喵币")
    private Integer consumer;

    @ApiModelProperty(value = "积分付款X喵币=1元")
    private Integer money;

    @ApiModelProperty(value = "每日签到喵币")
    private Integer signIn;

    @ApiModelProperty(value = "订单评价赠送喵币")
    private Integer comment;

    @ApiModelProperty(value = "喵币具体设置")
    private List<PointSettingItem> pointSettingItems = new ArrayList<>();

    public Integer getRegister() {
        if (register == null || register < 0) {
            return 0;
        }
        return register;
    }

    public Integer getMoney() {
        if (money == null || money < 0) {
            return 0;
        }
        return money;
    }

    public Integer getConsumer() {
        if (consumer == null || consumer < 0) {
            return 0;
        }
        return consumer;
    }

    public Integer getSignIn() {
        if (signIn == null || signIn < 0) {
            return 0;
        }
        return signIn;
    }

    public Integer getComment() {
        if (comment == null || comment < 0) {
            return 0;
        }
        return comment;
    }
}
