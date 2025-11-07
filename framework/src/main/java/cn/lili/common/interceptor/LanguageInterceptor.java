package cn.lili.common.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 语言拦截器
 * 从HTTP Header中获取语言，存入ThreadLocal
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
        
        // 2. 默认中文
        if (lang == null || lang.trim().isEmpty()) {
            lang = "zh-CN";
        }
        
        // 3. 标准化语言代码
        lang = normalizeLang(lang);
        
        // 4. 存入ThreadLocal（供后续使用）
        request.setAttribute("currentLang", lang);
        
        log.debug("当前请求语言: {}", lang);
        
        return true;
    }

    /**
     * 标准化语言代码
     *
     * @param lang 原始语言代码
     * @return 标准化后的语言代码
     */
    private String normalizeLang(String lang) {
        if (lang == null) {
            return "zh-CN";
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
        lang = lang.trim().replace("_", "-");

        // 映射简写
        switch (lang.toLowerCase()) {
            case "zh":
            case "zh-cn":
            case "cn":
                return "zh-CN";
            case "en":
            case "en-us":
                return "en-US";
            case "ja":
            case "ja-jp":
                return "ja-JP";
            case "ko":
            case "ko-kr":
                return "ko-KR";
            case "th":
            case "th-th":
                return "th-TH";
            case "es":
            case "es-es":
                return "es-ES";
            case "fr":
            case "fr-fr":
                return "fr-FR";
            case "vi":
            case "vi-vn":
                return "vi-VN";
            default:
                return "zh-CN";
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理ThreadLocal，防止内存泄漏
        request.removeAttribute("currentLang");
    }
}

