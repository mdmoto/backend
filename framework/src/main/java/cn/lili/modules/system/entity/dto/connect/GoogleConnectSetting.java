package cn.lili.modules.system.entity.dto.connect;

import lombok.Data;

/**
 * Google OAuth 2.0 联合登录设置
 *
 * @author Maollar
 * @since 2025-11-06
 */
@Data
public class GoogleConnectSetting {

    /**
     * Google OAuth Client ID
     */
    private String clientId;

    /**
     * Google OAuth Client Secret
     */
    private String clientSecret;

    /**
     * 重定向URI（回调地址）
     */
    private String redirectUri;

    /**
     * 是否启用
     */
    private Boolean enabled = true;
}

