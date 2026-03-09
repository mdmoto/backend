package cn.lili.controller.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * spring Security 核心配置类 通用安全
 *
 * @author Chopper
 * @version v4.0
 * @since 2020/11/14 16:20
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class ImSecurityConfig {

    /**
     * spring security -》 权限不足处理
     */
    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .build();
    }

}
