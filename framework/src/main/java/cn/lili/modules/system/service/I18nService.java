package cn.lili.modules.system.service;

import cn.hutool.core.text.StrFormatter;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.system.entity.dos.I18nTranslation;
import cn.lili.modules.system.mapper.I18nTranslationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多语言翻译服务
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Slf4j
@Service
public class I18nService {

    @Autowired
    private I18nTranslationMapper i18nTranslationMapper;

    /**
     * 根据语言获取翻译
     *
     * @param key  翻译key
     * @param lang 语言代码 zh-CN, en-US等
     * @return 翻译文本
     */
    @Cacheable(value = "i18n", key = "#key + '_' + #lang")
    public String translate(String key, String lang) {
        if (key == null || key.trim().isEmpty()) {
            return key;
        }

        try {
            I18nTranslation translation = getTranslation(key);
            if (translation == null) {
                log.warn("翻译key不存在: {}", key);
                return key; // 找不到翻译，返回key本身
            }

            String result = getTranslationByLang(translation, lang);
            
            // 如果指定语言没有翻译，回退到中文
            if (result == null || result.trim().isEmpty()) {
                log.warn("语言 {} 的翻译不存在，使用中文: {}", lang, key);
                return translation.getZhCn() != null ? translation.getZhCn() : key;
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("翻译失败: key={}, lang={}", key, lang, e);
            return key;
        }
    }

    /**
     * 带参数的翻译
     *
     * @param key    翻译key
     * @param lang   语言代码
     * @param params 参数Map
     * @return 翻译文本
     */
    public String translate(String key, String lang, Map<String, Object> params) {
        String template = translate(key, lang);
        
        if (params == null || params.isEmpty()) {
            return template;
        }

        try {
            // 替换参数 {orderSn} → 实际订单号
            // 使用Hutool的StrFormatter处理参数替换
            return StrFormatter.format(template, params);
        } catch (Exception e) {
            log.error("参数替换失败: key={}, params={}", key, params, e);
            return template;
        }
    }

    /**
     * 带JSON参数的翻译
     *
     * @param key        翻译key
     * @param lang       语言代码
     * @param paramsJson JSON格式的参数
     * @return 翻译文本
     */
    public String translate(String key, String lang, String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return translate(key, lang);
        }

        try {
            Map<String, Object> params = JSONUtil.toBean(paramsJson, Map.class);
            return translate(key, lang, params);
        } catch (Exception e) {
            log.error("JSON参数解析失败: key={}, paramsJson={}", key, paramsJson, e);
            return translate(key, lang);
        }
    }

    /**
     * 批量翻译
     *
     * @param keys 翻译key列表
     * @param lang 语言代码
     * @return key -> 翻译文本的Map
     */
    public Map<String, String> batchTranslate(List<String> keys, String lang) {
        Map<String, String> result = new HashMap<>();
        
        for (String key : keys) {
            result.put(key, translate(key, lang));
        }
        
        return result;
    }

    /**
     * 获取翻译对象（带缓存）
     *
     * @param key 翻译key
     * @return 翻译对象
     */
    @Cacheable(value = "i18n_obj", key = "#key")
    public I18nTranslation getTranslation(String key) {
        LambdaQueryWrapper<I18nTranslation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(I18nTranslation::getTranslationKey, key);
        queryWrapper.eq(I18nTranslation::getDeleteFlag, false);
        return i18nTranslationMapper.selectOne(queryWrapper);
    }

    /**
     * 根据语言代码获取对应的翻译文本
     *
     * @param translation 翻译对象
     * @param lang        语言代码
     * @return 翻译文本
     */
    private String getTranslationByLang(I18nTranslation translation, String lang) {
        if (translation == null) {
            return null;
        }

        // 处理语言代码格式（zh-CN, zh_CN, zhCN都支持）
        String normalizedLang = normalizeLangCode(lang);

        switch (normalizedLang) {
            case "zh-CN":
            case "zh_CN":
            case "zhCN":
                return translation.getZhCn();
            case "en-US":
            case "en_US":
            case "enUS":
            case "en":
                return translation.getEnUs();
            case "ja-JP":
            case "ja_JP":
            case "jaJP":
            case "ja":
                return translation.getJaJp();
            case "ko-KR":
            case "ko_KR":
            case "koKR":
            case "ko":
                return translation.getKoKr();
            case "th-TH":
            case "th_TH":
            case "thTH":
            case "th":
                return translation.getThTh();
            case "es-ES":
            case "es_ES":
            case "esES":
            case "es":
                return translation.getEsEs();
            case "fr-FR":
            case "fr_FR":
            case "frFR":
            case "fr":
                return translation.getFrFr();
            case "vi-VN":
            case "vi_VN":
            case "viVN":
            case "vi":
                return translation.getViVn();
            default:
                // 默认返回中文
                return translation.getZhCn();
        }
    }

    /**
     * 标准化语言代码
     *
     * @param lang 原始语言代码
     * @return 标准化后的语言代码
     */
    private String normalizeLangCode(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return "zh-CN";
        }
        
        // 统一转换为小写，然后处理
        String lower = lang.toLowerCase().replace("_", "-");
        
        // 处理简写（en → en-US）
        if (lower.equals("zh")) return "zh-CN";
        if (lower.equals("en")) return "en-US";
        if (lower.equals("ja")) return "ja-JP";
        if (lower.equals("ko")) return "ko-KR";
        if (lower.equals("th")) return "th-TH";
        if (lower.equals("es")) return "es-ES";
        if (lower.equals("fr")) return "fr-FR";
        if (lower.equals("vi")) return "vi-VN";
        
        return lang;
    }

    /**
     * 清除翻译缓存
     */
    public void clearCache() {
        // 由Spring Cache管理，可以通过CacheManager清除
        log.info("清除i18n缓存");
    }
}

