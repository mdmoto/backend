package cn.lili.modules.connect.entity.enums;

/**
 * 联合登录配置
 *
 * @author Chopper
 * @version v1.0
 * 2020-11-25 18:23
 */
public enum ConnectConfigEnum {

    /**
     * Google OAuth 2.0
     */
    GOOGLE("Google登录配置", "client_id,client_secret,redirect_uri")
    ;

    /**
     * 名称
     */
    String name;

    /**
     * 表单项
     */
    String form;

    ConnectConfigEnum(String name, String form) {
        this.name = name;
        this.form = form;
    }

    public String getName() {
        return name;
    }

    public String getForm() {
        return form;
    }
}
