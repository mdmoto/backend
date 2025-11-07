package cn.lili.modules.connect.request;

import cn.lili.cache.Cache;
import cn.lili.common.enums.ClientTypeEnum;
import cn.lili.common.utils.HttpUtils;
import cn.lili.common.utils.UrlBuilder;
import cn.lili.modules.connect.config.AuthConfig;
import cn.lili.modules.connect.config.ConnectAuthEnum;
import cn.lili.modules.connect.entity.dto.AuthCallback;
import cn.lili.modules.connect.entity.dto.AuthResponse;
import cn.lili.modules.connect.entity.dto.AuthToken;
import cn.lili.modules.connect.entity.dto.ConnectAuthUser;
import cn.lili.modules.connect.entity.enums.AuthResponseStatus;
import cn.lili.modules.connect.entity.enums.AuthUserGender;
import cn.lili.modules.connect.entity.enums.ConnectEnum;
import cn.lili.modules.connect.entity.enums.SourceEnum;
import cn.lili.modules.connect.exception.AuthException;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * Google OAuth 2.0 登录
 *
 * @author Maollar
 * @since 1.0.0
 */
@Slf4j
public class BaseAuthGoogleRequest extends BaseAuthRequest {

    public BaseAuthGoogleRequest(AuthConfig config, Cache cache) {
        super(config, ConnectAuthEnum.GOOGLE, cache);
    }

    /**
     * 获取授权 URL
     */
    @Override
    public String authorize(String state) {
        return UrlBuilder.fromBaseUrl(source.authorize())
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("state", getRealState(state))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .build();
    }

    /**
     * 获取 Access Token
     */
    @Override
    protected AuthToken getAccessToken(AuthCallback authCallback) {
        String response = doPostAuthorizationCode(authCallback.getCode());
        JSONObject accessTokenObject = JSONObject.parseObject(response);

        this.checkResponse(accessTokenObject);

        return AuthToken.builder()
                .accessToken(accessTokenObject.getString("access_token"))
                .refreshToken(accessTokenObject.getString("refresh_token"))
                .idToken(accessTokenObject.getString("id_token"))
                .tokenType(accessTokenObject.getString("token_type"))
                .scope(accessTokenObject.getString("scope"))
                .expireIn(accessTokenObject.getIntValue("expires_in"))
                .build();
    }

    /**
     * 获取用户信息
     */
    @Override
    protected ConnectAuthUser getUserInfo(AuthToken authToken) {
        String accessToken = authToken.getAccessToken();
        String response = doGetUserInfo(authToken);
        JSONObject userInfo = JSONObject.parseObject(response);

        this.checkResponse(userInfo);

        String userId = userInfo.getString("id");
        String email = userInfo.getString("email");
        String name = userInfo.getString("name");
        String picture = userInfo.getString("picture");
        Boolean emailVerified = userInfo.getBoolean("verified_email");

        return ConnectAuthUser.builder()
                .rawUserInfo(userInfo)
                .uuid(userId)
                .username(email)
                .nickname(name)
                .avatar(picture)
                .email(email)
                .gender(AuthUserGender.UNKNOWN)
                .token(authToken)
                .source(ConnectEnum.GOOGLE)
                .type(ClientTypeEnum.H5) // 可以根据实际情况调整
                .build();
    }

    /**
     * 刷新 Token
     */
    @Override
    public AuthResponse refresh(AuthToken oldToken) {
        String refreshTokenUrl = UrlBuilder.fromBaseUrl(source.accessToken())
                .queryParam("client_id", config.getClientId())
                .queryParam("client_secret", config.getClientSecret())
                .queryParam("refresh_token", oldToken.getRefreshToken())
                .queryParam("grant_type", "refresh_token")
                .build();

        String response = new HttpUtils(config.getHttpConfig()).post(refreshTokenUrl);
        JSONObject refreshTokenObject = JSONObject.parseObject(response);

        this.checkResponse(refreshTokenObject);

        return AuthResponse.builder()
                .code(AuthResponseStatus.SUCCESS.getCode())
                .data(AuthToken.builder()
                        .accessToken(refreshTokenObject.getString("access_token"))
                        .idToken(refreshTokenObject.getString("id_token"))
                        .tokenType(refreshTokenObject.getString("token_type"))
                        .scope(refreshTokenObject.getString("scope"))
                        .expireIn(refreshTokenObject.getIntValue("expires_in"))
                        .refreshToken(oldToken.getRefreshToken())
                        .build())
                .build();
    }

    /**
     * 检查响应是否包含错误
     */
    private void checkResponse(JSONObject object) {
        if (object.containsKey("error")) {
            String errorDescription = object.getString("error_description");
            String error = object.getString("error");
            log.error("Google OAuth error: {}, description: {}", error, errorDescription);
            throw new AuthException(error + ": " + errorDescription);
        }
    }

    /**
     * POST 请求获取 Authorization Code
     */
    protected String doPostAuthorizationCode(String code) {
        String url = UrlBuilder.fromBaseUrl(source.accessToken())
                .queryParam("code", code)
                .queryParam("client_id", config.getClientId())
                .queryParam("client_secret", config.getClientSecret())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("grant_type", "authorization_code")
                .build();

        return new HttpUtils(config.getHttpConfig()).post(url);
    }

    /**
     * 获取用户信息
     */
    protected String doGetUserInfo(AuthToken authToken) {
        String url = UrlBuilder.fromBaseUrl(source.userInfo())
                .queryParam("access_token", authToken.getAccessToken())
                .build();
        return new HttpUtils(config.getHttpConfig()).get(url);
    }
}

