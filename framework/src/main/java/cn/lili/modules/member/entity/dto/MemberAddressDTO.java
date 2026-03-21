package cn.lili.modules.member.entity.dto;

import cn.lili.common.validation.Phone;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 会员地址DTO
 *
 * @author Bulbasaur
 * @since 2020/12/14 16:31
 */
@Data
public class MemberAddressDTO {

    @NotEmpty(message = "收货人姓名不能为空")
    @ApiModelProperty(value = "收货人姓名")
    private String consigneeName;

    @Phone
    @NotBlank(message = "手机号码不能为空")
    @ApiModelProperty(value = "手机号码")
    private String consigneeMobile;

    @ApiModelProperty(value = "国家/地区(ISO 3166-1 alpha-2)，如 CN/US/JP")
    private String countryCode;

    @ApiModelProperty(value = "邮编/邮政编码")
    private String postalCode;

    @NotBlank(message = "地址不能为空")
    @ApiModelProperty(value = "地址名称， '，'分割")
    private String consigneeAddressPath;

    @ApiModelProperty(value = "地址id，'，'分割 ")
    private String consigneeAddressIdPath;

    @NotEmpty(message = "详细地址不能为空")
    @ApiModelProperty(value = "详细地址")
    private String consigneeDetail;
}
