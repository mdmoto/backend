package cn.lili.security;

import cn.lili.cache.Cache;
import cn.lili.common.security.CustomAccessDeniedHandler;
import cn.lili.common.utils.SpringContextUtil;
import cn.lili.common.properties.IgnoredUrlsProperties;
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
 * spring Security 核心配置类 Buyer安全配置中心
 *
 * @author Chopper
 * @version v4.0
 * @since 2020/11/14 16:20
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BuyerSecurityConfig {

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> {
                    // 配置的url 不需要授权
                    for (String url : ignoredUrlsProperties.getUrls()) {
                        authorize.requestMatchers(url).permitAll();
                    }
                    authorize.requestMatchers("/actuator/**").permitAll();
                    authorize.requestMatchers("/api/v1/ai/**").permitAll();
                    authorize.requestMatchers("/api/v1/maollar/rates", "/api/v1/maollar/tier-status",
                                    "/api/v1/maollar/merkle-root", "/api/v1/maollar/exchange-log",
                                    "/api/v1/other/**", "/api/v1/goods/**")
                            .permitAll();


                    authorize.anyRequest().authenticated();
                })
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .logout(logout -> logout.permitAll())
                .cors(cors -> cors.configurationSource(
                        (CorsConfigurationSource) SpringContextUtil.getBean("corsConfigurationSource")))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.accessDeniedHandler(accessDeniedHandler))
                .addFilterAt(new BuyerAuthenticationFilter(authenticationManager, cache),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}
