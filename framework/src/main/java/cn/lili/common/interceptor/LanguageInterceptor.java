package cn.lili.common.interceptor;

import cn.lili.common.context.LanguageContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 语言拦截器
 * 从HTTP Header中获取语言，存入LanguageContextHolder
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Slf4j
@Component
public class LanguageInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从Header获取语言
        String lang = request.getHeader("Accept-Language");

        // 2. 标准化语言代码
        lang = normalizeLang(lang);

        // 3. 存入ContextHolder
        LanguageContextHolder.set(lang);

        log.debug("当前请求语言: {}", lang);

        return true;
    }

    /**
     * 标准化语言代码 (适配 9 种语言: en, zh, ja, ko, ar, es, fr, th, de)
     *
     * @param lang 原始语言代码
     * @return 标准化后的短语言代码
     */
    private String normalizeLang(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return "en";
        }

        // 处理浏览器可能发送的格式：zh-CN,zh;q=0.9,en;q=0.8
        if (lang.contains(",")) {
            lang = lang.split(",")[0];
        }

        // 处理权重：zh-CN;q=0.9
        if (lang.contains(";")) {
            lang = lang.split(";")[0];
        }

        // 统一格式
        lang = lang.trim().replace("_", "-").toLowerCase();

        // 映射为短代码
        if (lang.startsWith("zh"))
            return "zh";
        if (lang.startsWith("en"))
            return "en";
        if (lang.startsWith("ja"))
            return "ja";
        if (lang.startsWith("ko"))
            return "ko";
        if (lang.startsWith("ar"))
            return "ar";
        if (lang.startsWith("es"))
            return "es";
        if (lang.startsWith("fr"))
            return "fr";
        if (lang.startsWith("th"))
            return "th";
        if (lang.startsWith("de"))
            return "de";

        return "en";
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        // 清理ThreadLocal，防止内存泄漏
        LanguageContextHolder.remove();
    }
}
