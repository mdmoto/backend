package cn.lili.security;

import cn.lili.cache.Cache;
import cn.lili.common.properties.IgnoredUrlsProperties;
import cn.lili.common.security.CustomAccessDeniedHandler;
import cn.lili.modules.permission.service.MenuService;
import cn.lili.modules.system.token.ManagerTokenGenerate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * spring Security 核心配置类 Manager安全配置中心 (Full Upgrade to Security 6)
 *
 * @author Chopper
 * @since 2020/11/14 16:20
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ManagerSecurityConfig {

    @Autowired
    private MenuService menuService;

    /**
     * 忽略验权配置
     */
    @Autowired
    private IgnoredUrlsProperties ignoredUrlsProperties;

    /**
     * spring security -》 权限不足处理
     */
    @Autowired
    private CustomAccessDeniedHandler accessDeniedHandler;

    @Autowired
    private Cache<String> cache;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private ManagerTokenGenerate managerTokenGenerate;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> {
                    // 配置的url 不需要授权
                    for (String url : ignoredUrlsProperties.getUrls()) {
                        authorize.requestMatchers(url).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.accessDeniedHandler(accessDeniedHandler))
                .addFilterAt(new ManagerAuthenticationFilter(authenticationManager, menuService, managerTokenGenerate,
                        cache), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}
