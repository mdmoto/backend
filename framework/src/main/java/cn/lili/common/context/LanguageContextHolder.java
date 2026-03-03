package cn.lili.common.context;

/**
 * 语言上下文持有者
 *
 * @author MaoMall
 * @since 2026-03-03
 */
public class LanguageContextHolder {

    private static final ThreadLocal<String> LANGUAGE_HOLDER = new ThreadLocal<>();

    private static final String DEFAULT_LANGUAGE = "zh";

    /**
     * 设置语言
     * 
     * @param lang 语言代码
     */
    public static void set(String lang) {
        LANGUAGE_HOLDER.set(lang);
    }

    /**
     * 获取语言
     * 
     * @return 语言代码
     */
    public static String get() {
        String lang = LANGUAGE_HOLDER.get();
        return lang != null ? lang : DEFAULT_LANGUAGE;
    }

    /**
     * 清除语言
     */
    public static void remove() {
        LANGUAGE_HOLDER.remove();
    }
}
