package cn.lili.modules.openapi.aop;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpenApiRateLimit {
    
    /**
     * 接口名称标识
     */
    String name() default "";

    /**
     * 每分钟限制次数
     */
    long minLimit() default 1000;

    /**
     * 每天限制次数
     */
    long dayLimit() default 50000;
}
