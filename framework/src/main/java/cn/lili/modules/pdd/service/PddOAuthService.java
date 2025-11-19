package cn.lili.modules.pdd.service;

import cn.lili.modules.pdd.dto.PddCredential;
import cn.lili.modules.pdd.dto.PddToken;

import java.util.Optional;

/**
 * 拼多多 OAuth 服务.
 */
public interface PddOAuthService {

    /**
     * 保存/更新拼多多凭证配置.
     *
     * @param credential 凭证
     */
    void saveCredential(PddCredential credential);

    /**
     * 获取当前的拼多多凭证配置.
     *
     * @return 凭证
     */
    Optional<PddCredential> getCredential();

    /**
     * 处理 OAuth 回调，使用授权码换取 access_token 并保存.
     *
     * @param code  授权码
     * @param state 状态
     * @return 令牌信息
     */
    PddToken handleAuthorizationCallback(String code, String state);

    /**
     * 获取最近保存的 Token.
     *
     * @return Token 信息
     */
    Optional<PddToken> getToken();
}

