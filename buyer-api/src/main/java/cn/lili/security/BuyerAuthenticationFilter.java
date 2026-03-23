package cn.lili.security;

import cn.hutool.core.util.StrUtil;
import cn.lili.cache.Cache;
import cn.lili.cache.CachePrefix;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.enums.SecurityEnum;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.security.token.SecretKeyUtil;
import cn.lili.common.utils.ResponseUtil;
import com.google.gson.Gson;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证结果过滤器
 *
 * @author Chopper
 * @version v4.1
 * @since 2020/11/17 3:37 下午
 * @since
 */
@Slf4j
public class BuyerAuthenticationFilter extends BasicAuthenticationFilter {

    /**
     * 缓存
     */
    @Autowired
    private Cache cache;

    /**
     * 自定义构造器
     *
     * @param authenticationManager
     * @param cache
     */
    public BuyerAuthenticationFilter(AuthenticationManager authenticationManager,
            Cache cache) {
        super(authenticationManager);
        this.cache = cache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    // DEBUG: LOG ALL REQUESTS TO IDENTIFY 403 SOURCE
    log.info("【DEBUG】Buyer Filter Processing URI: {}",request.getRequestURI());

    String uri = request.getRequestURI();
    // 如果是标准的 v1 或 legacy /buyer 公共路径，直接放行，不校验 Token
    if(uri.startsWith("/api/v1/other")||uri.startsWith("/api/v1/maollar/rates")||uri.startsWith("/api/v1/goods")
            ||uri.startsWith("/buyer/other")||uri.startsWith("/buyer/maollar")||uri.startsWith("/buyer/goods")
            || uri.startsWith("/api/v1/mao-proxy") || uri.startsWith("/buyer/mao-proxy"))
    {
        chain.doFilter(request, response);
        return;
    }

    // 从header中获取jwt
    String jwt = request.getHeader(SecurityEnum.HEADER_TOKEN.getValue());try
    {
        // 如果没有token 则return
        if (StrUtil.isBlank(jwt)) {
            chain.doFilter(request, response);
            return;
        }
        // 获取用户信息，存入context
        UsernamePasswordAuthenticationToken authentication = getAuthentication(jwt, response);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }catch(
    Exception e)
    {
        log.error("BuyerAuthenticationFilter-> member authentication exception:", e);
    }chain.doFilter(request,response);
    }

    /**
     * 解析用户
     *
     * @param jwt
     * @param response
     * @return
     */
    private UsernamePasswordAuthenticationToken getAuthentication(String jwt, HttpServletResponse response) {

        try {
            Claims claims
                    = Jwts.parserBuilder()
                    .setSigningKey(SecretKeyUtil.generalKeyByDecoders())
                    .build()
                    .parseClaimsJws(jwt).getBody();
            // 获取存储在claims中的用户信息
            String json = claims.get(SecurityEnum.USER_CONTEXT.getValue()).toString();
            AuthUser authUser = new Gson().fromJson(json, AuthUser.class);

            //校验redis中是否有权限
            if (cache.hasKey(CachePrefix.ACCESS_TOKEN.getPrefix(UserEnums.MEMBER, authUser.getId()) + jwt)) {
                //构造返回信息
                List<GrantedAuthority> auths = new ArrayList<>();
                auths.add(new SimpleGrantedAuthority("ROLE_" + authUser.getRole().name()));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(authUser.getUsername(), null, auths);
                authentication.setDetails(authUser);
                return authentication;
            }
            ResponseUtil.output(response, 403, ResponseUtil.resultMap(false, 403, "登录已失效，请重新登录"));
            return null;
        } catch (ExpiredJwtException e) {
            log.debug("user analysis exception:", e);
        } catch (Exception e) {
            log.error("user analysis exception:", e);
        }
        return null;
    }

}
