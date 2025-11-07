package cn.lili.common.utils;

import cn.lili.modules.system.service.I18nService;
import cn.lili.common.context.ThreadContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * i18n工具类（静态访问）
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Slf4j
@Component
public class I18nUtil {

    @Autowired
    private I18nService i18nService;

    private static I18nService staticI18nService;

    @PostConstruct
    public void init() {
        staticI18nService = i18nService;
    }

    /**
     * 翻译（自动获取当前请求的语言）
     *
     * @param key 翻译key
     * @return 翻译文本
     */
    public static String t(String key) {
        String lang = getCurrentLang();
        return t(key, lang);
    }

    /**
     * 翻译（指定语言）
     *
     * @param key  翻译key
     * @param lang 语言代码
     * @return 翻译文本
     */
    public static String t(String key, String lang) {
        if (staticI18nService == null) {
            log.warn("I18nService未初始化，返回原始key: {}", key);
            return key;
        }
        return staticI18nService.translate(key, lang);
    }

    /**
     * 带参数的翻译
     *
     * @param key    翻译key
     * @param params 参数Map
     * @return 翻译文本
     */
    public static String t(String key, Map<String, Object> params) {
        String lang = getCurrentLang();
        return t(key, lang, params);
    }

    /**
     * 带参数的翻译（指定语言）
     *
     * @param key    翻译key
     * @param lang   语言代码
     * @param params 参数Map
     * @return 翻译文本
     */
    public static String t(String key, String lang, Map<String, Object> params) {
        if (staticI18nService == null) {
            log.warn("I18nService未初始化，返回原始key: {}", key);
            return key;
        }
        return staticI18nService.translate(key, lang, params);
    }

    /**
     * 获取当前请求的语言
     *
     * @return 语言代码，默认zh-CN
     */
    private static String getCurrentLang() {
        try {
            // 从ThreadLocal获取当前请求的语言
            // 由拦截器设置
            String lang = (String) ThreadContextHolder.getHttpRequest().getHeader("Accept-Language");
            return lang != null ? lang : "zh-CN";
        } catch (Exception e) {
            return "zh-CN";
        }
    }
}

