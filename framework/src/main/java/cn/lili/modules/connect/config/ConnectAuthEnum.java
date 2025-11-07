package cn.lili.modules.connect.config;


/**
 * 用户信息 枚举
 *
 * @author Chopper
 * @version v4.0
 * @since 2020/12/4 14:10
 */
public enum ConnectAuthEnum implements ConnectAuth {

    /**
     * Google OAuth 2.0
     */
    GOOGLE {
        @Override
        public String authorize() {
            return "https://accounts.google.com/o/oauth2/v2/auth";
        }

        @Override
        public String accessToken() {
            return "https://oauth2.googleapis.com/token";
        }

        @Override
        public String userInfo() {
            return "https://www.googleapis.com/oauth2/v2/userinfo";
        }
    }

}
