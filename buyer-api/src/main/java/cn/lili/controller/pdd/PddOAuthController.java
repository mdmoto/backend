package cn.lili.controller.pdd;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.pdd.dto.PddToken;
import cn.lili.modules.pdd.service.PddOAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 拼多多 OAuth 授权回调控制器
 * 
 * 用于处理拼多多多多进宝推手的 OAuth 授权回调
 * 
 * @author Maollar
 */
@Slf4j
@RestController
@Api(tags = "拼多多 OAuth 授权回调")
@RequestMapping("/pdd/oauth")
public class PddOAuthController {

    @Autowired
    private PddOAuthService pddOAuthService;

    /**
     * 拼多多 OAuth 授权回调接口
     * 
     * 拼多多授权完成后会回调此接口，携带 code 和 state 参数
     * 
     * @param code  授权码
     * @param state 状态参数（可选）
     * @param response HTTP 响应
     */
    @GetMapping("/callback")
    @ApiOperation(value = "拼多多 OAuth 授权回调", hidden = true)
    public void callback(
            @ApiParam(value = "授权码", required = true) @RequestParam(required = false) String code,
            @ApiParam(value = "状态参数") @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {
        
        log.info("收到拼多多 OAuth 回调: code={}, state={}", code, state);
        
        if (!StringUtils.hasText(code)) {
            log.error("拼多多 OAuth 回调缺少授权码 code");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "缺少授权码 code");
            return;
        }
        
        try {
            // 使用授权码换取 access_token
            PddToken token = pddOAuthService.handleAuthorizationCallback(code, state);
            
            log.info("拼多多 OAuth 授权成功: ownerId={}, mallId={}, expiresAt={}", 
                    token.getOwnerId(), token.getMallId(), token.getExpiresAt());
            
            // 重定向到成功页面或管理后台
            // 这里可以重定向到管理后台的拼多多配置页面
            response.sendRedirect("https://admin.maollar.com/#/sys/setting-manage?type=PDD_OAUTH");
            
        } catch (ServiceException e) {
            log.error("拼多多 OAuth 授权失败: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "授权失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("拼多多 OAuth 授权处理异常", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "处理授权回调时发生异常: " + e.getMessage());
        }
    }

    /**
     * 获取当前 Token 信息（用于测试）
     */
    @GetMapping("/token")
    @ApiOperation(value = "获取当前拼多多 Token 信息")
    public ResultMessage<PddToken> getToken() {
        return pddOAuthService.getToken()
                .map(ResultUtil::data)
                .orElseThrow(() -> new ServiceException(ResultCode.ERROR, "未找到拼多多 Token，请先完成授权"));
    }
}

