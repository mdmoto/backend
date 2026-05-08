package cn.lili.modules.openapi.interceptor;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.context.UserContext;
import cn.lili.modules.openapi.entity.OpenApiKey;
import cn.lili.modules.openapi.service.OpenApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private OpenApiKeyService openApiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = request.getHeader("API-KEY");
        String apiSecret = request.getHeader("API-SECRET");

        if (apiKey == null || apiSecret == null) {
            response.setStatus(401);
            response.getWriter().write("Missing API-KEY or API-SECRET headers");
            return false;
        }

        OpenApiKey keyObj = openApiKeyService.getByApiKey(apiKey);
        if (keyObj == null || !keyObj.getApiSecret().equals(apiSecret)) {
            response.setStatus(401);
            response.getWriter().write("Invalid API-KEY or API-SECRET");
            return false;
        }

        if (!"OPEN".equals(keyObj.getStatus())) {
            response.setStatus(403);
            response.getWriter().write("API Key is disabled");
            return false;
        }

        // Check path permissions
        String uri = request.getRequestURI();
        String requiredPermission = null;
        if (uri.contains("/openapi/product")) {
            requiredPermission = "Read:Product";
        } else if (uri.contains("/openapi/order")) {
            requiredPermission = "Write:Order";
        }

        if (requiredPermission != null && !keyObj.getPermissions().contains(requiredPermission)) {
            response.setStatus(403);
            response.getWriter().write("Missing required permission: " + requiredPermission);
            return false;
        }

        // Inject member ID into attributes so CheckBalanceInterceptor and Controllers can use it
        request.setAttribute("OpenApiMemberId", keyObj.getMemberId());

        cn.lili.common.security.AuthUser authUser = new cn.lili.common.security.AuthUser();
        authUser.setId(keyObj.getMemberId());
        authUser.setRole(cn.lili.common.security.enums.UserEnums.MEMBER);
        request.setAttribute("OpenApiUser", authUser);

        return true;
    }
}
