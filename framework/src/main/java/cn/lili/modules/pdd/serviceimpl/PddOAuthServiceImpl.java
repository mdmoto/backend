package cn.lili.modules.pdd.serviceimpl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.MD5;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.utils.HttpUtils;
import cn.lili.modules.pdd.dto.PddCredential;
import cn.lili.modules.pdd.dto.PddToken;
import cn.lili.modules.pdd.service.PddOAuthService;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 拼多多 OAuth 服务实现.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PddOAuthServiceImpl implements PddOAuthService {

    /**
     * 用于保存凭证的 Setting key.
     */
    static final String CREDENTIAL_KEY = "PDD_OAUTH_CREDENTIAL";

    /**
     * 用于保存 Token 的 Setting key.
     */
    static final String TOKEN_KEY = "PDD_OAUTH_TOKEN";

    /**
     * 拼多多 POP API 端点（根据官方文档）
     */
    private static final String API_ENDPOINT = "https://gw-api.pinduoduo.com/api/router";
    
    /**
     * Token 创建接口类型
     */
    private static final String TOKEN_CREATE_TYPE = "pdd.pop.auth.token.create";

    private final SettingService settingService;

    @Value("${integrations.pdd.client-id:}")
    private String defaultClientId;

    @Value("${integrations.pdd.client-secret:}")
    private String defaultClientSecret;

    @Value("${integrations.pdd.redirect-uri:}")
    private String defaultRedirectUri;

    @Override
    public void saveCredential(PddCredential credential) {
        if (credential == null
                || !StringUtils.hasText(credential.getClientId())
                || !StringUtils.hasText(credential.getClientSecret())
                || !StringUtils.hasText(credential.getRedirectUri())) {
            throw new ServiceException(ResultCode.ERROR, "拼多多凭证信息不完整");
        }
        Setting setting = Optional.ofNullable(settingService.get(CREDENTIAL_KEY))
                .orElseGet(Setting::new);
        setting.setId(CREDENTIAL_KEY);
        setting.setSettingValue(JSONUtil.toJsonStr(credential));
        settingService.saveUpdate(setting);
    }

    @Override
    public Optional<PddCredential> getCredential() {
        // 优先读取数据库
        Setting setting = settingService.get(CREDENTIAL_KEY);
        if (setting != null && CharSequenceUtil.isNotBlank(setting.getSettingValue())) {
            try {
                return Optional.of(JSONUtil.toBean(setting.getSettingValue(), PddCredential.class));
            } catch (Exception e) {
                log.error("解析拼多多凭证配置失败: {}", setting.getSettingValue(), e);
            }
        }

        // 退回到配置文件
        if (CharSequenceUtil.isAllNotEmpty(defaultClientId, defaultClientSecret, defaultRedirectUri)) {
            PddCredential credential = new PddCredential();
            credential.setClientId(defaultClientId);
            credential.setClientSecret(defaultClientSecret);
            credential.setRedirectUri(defaultRedirectUri);
            return Optional.of(credential);
        }

        return Optional.empty();
    }

    @Override
    public PddToken handleAuthorizationCallback(String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new ServiceException(ResultCode.ERROR, "授权码 code 不能为空");
        }
        PddCredential credential = getCredential()
                .orElseThrow(() -> new ServiceException(ResultCode.ERROR, "未配置拼多多凭证，请先完成凭证设置"));

        // 根据官方文档，使用 pdd.pop.auth.token.create 接口
        // 需要按照参数名排序并生成 MD5 签名
        long timestamp = Instant.now().getEpochSecond();
        Map<String, String> params = new TreeMap<>();
        params.put("type", TOKEN_CREATE_TYPE);
        params.put("client_id", credential.getClientId());
        params.put("data_type", "JSON");
        params.put("code", code);
        params.put("timestamp", String.valueOf(timestamp));

        // 生成签名：MD5(client_secret + 排序后的参数字符串 + client_secret).toUpperCase()
        StringBuilder signStr = new StringBuilder(credential.getClientSecret());
        for (Map.Entry<String, String> entry : params.entrySet()) {
            signStr.append(entry.getKey()).append(entry.getValue());
        }
        signStr.append(credential.getClientSecret());
        String sign = MD5.create().digestHex(signStr.toString(), StandardCharsets.UTF_8).toUpperCase();
        params.put("sign", sign);

        String response = HttpUtils.doPost(API_ENDPOINT, params, StandardCharsets.UTF_8.name(),
                HttpUtils.HTTP_CONN_TIMEOUT, HttpUtils.HTTP_SOCKET_TIMEOUT);

        if (!StringUtils.hasText(response)) {
            throw new ServiceException(ResultCode.ERROR, "拼多多返回空响应，请稍后重试");
        }

        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("error_response")) {
            JSONObject error = json.getJSONObject("error_response");
            String errorMsg = error.getStr("error_msg", "拼多多授权失败");
            Integer errorCode = error.getInt("error_code");
            String subMsg = error.getStr("sub_msg");
            log.error("拼多多授权失败 error_code={}, error_msg={}, sub_msg={}", errorCode, errorMsg, subMsg);
            throw new ServiceException(ResultCode.ERROR, "拼多多授权失败：" + errorMsg + (subMsg != null ? " (" + subMsg + ")" : ""));
        }

        // 解析响应：pop_auth_token_create_response
        JSONObject tokenResponse = json.getJSONObject("pop_auth_token_create_response");
        if (tokenResponse == null) {
            log.error("拼多多授权响应格式错误: {}", json);
            throw new ServiceException(ResultCode.ERROR, "拼多多授权响应格式错误");
        }

        PddToken token = new PddToken();
        token.setAccessToken(tokenResponse.getStr("access_token"));
        token.setRefreshToken(tokenResponse.getStr("refresh_token"));
        Long expiresIn = tokenResponse.getLong("expires_in");
        if (expiresIn == null) {
            throw new ServiceException(ResultCode.ERROR, "拼多多授权响应缺少 expires_in");
        }
        token.setExpiresIn(expiresIn);
        token.setRefreshTokenExpiresIn(tokenResponse.getLong("refresh_token_expires_in"));
        token.setScope(tokenResponse.getStr("scope"));
        token.setOwnerId(tokenResponse.getLong("owner_id"));
        token.setOwnerName(tokenResponse.getStr("owner_name"));
        token.setMallId(tokenResponse.getLong("mall_id"));
        token.setState(state);

        long nowEpochSecond = Instant.now().getEpochSecond();
        token.setFetchedAt(nowEpochSecond);
        token.setExpiresAt(nowEpochSecond + token.getExpiresIn());
        if (token.getRefreshTokenExpiresIn() != null) {
            token.setRefreshTokenExpiresAt(nowEpochSecond + token.getRefreshTokenExpiresIn());
        }

        persistToken(token);
        log.info("成功获取拼多多授权，ownerId={}, mallId={}, expiresAt={}",
                token.getOwnerId(), token.getMallId(), DateUtil.date(token.getExpiresAt() * 1000));

        return token;
    }

    @Override
    public Optional<PddToken> getToken() {
        Setting setting = settingService.get(TOKEN_KEY);
        if (setting == null || CharSequenceUtil.isBlank(setting.getSettingValue())) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSONUtil.toBean(setting.getSettingValue(), PddToken.class));
        } catch (Exception e) {
            log.error("解析拼多多Token失败: {}", setting.getSettingValue(), e);
            return Optional.empty();
        }
    }

    private void persistToken(PddToken token) {
        Setting setting = Optional.ofNullable(settingService.get(TOKEN_KEY))
                .orElseGet(Setting::new);
        setting.setId(TOKEN_KEY);
        setting.setSettingValue(JSONUtil.toJsonStr(token));
        settingService.saveUpdate(setting);
    }
}

