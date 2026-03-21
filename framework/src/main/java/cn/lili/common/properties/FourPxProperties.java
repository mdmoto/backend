package cn.lili.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 4PX 递四方配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lili.logistics.fourpx")
public class FourPxProperties {

    /**
     * App Key
     */
    private String appKey;

    /**
     * App Secret
     */
    private String appSecret;

    /**
     * 客户代码
     */
    private String customerCode;

    /**
     * API 基础地址 (例如 https://open.4px.com)
     */
    private String baseUrl = "https://open.4px.com";

    /**
     * 默认始发仓代码 (如 SZ, GZ)
     */
    private String defaultWarehouseCode = "SZ";
}
