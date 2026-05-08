package cn.lili.config;

import cn.lili.modules.openapi.interceptor.OpenApiAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenAPIWebConfig implements WebMvcConfigurer {

    @Autowired
    private OpenApiAuthInterceptor openApiAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiAuthInterceptor)
                .addPathPatterns("/buyer/openapi/product/**", "/buyer/openapi/order/**");

    }
}
