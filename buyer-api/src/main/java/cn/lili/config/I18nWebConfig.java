package cn.lili.config;

import cn.lili.common.interceptor.LanguageInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * i18n Web配置
 * 注册语言拦截器
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Configuration
public class I18nWebConfig implements WebMvcConfigurer {

    @Autowired
    private LanguageInterceptor languageInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(languageInterceptor)
                .addPathPatterns("/buyer/**", "/seller/**", "/manager/**")  // 拦截所有API
                .excludePathPatterns(
                        "/buyer/passport/login",     // 登录不拦截
                        "/buyer/passport/register",  // 注册不拦截
                        "/swagger-resources/**",     // Swagger不拦截
                        "/v2/api-docs/**"            // API文档不拦截
                );
    }
}

