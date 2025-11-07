package cn.lili.modules.system.service;

import cn.lili.modules.system.entity.dos.I18nTranslation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 多语言翻译Service
 *
 * @author Chopper
 * @since 2025-11-05
 */
public interface I18nTranslationService extends IService<I18nTranslation> {

    /**
     * 根据key获取翻译
     *
     * @param key 翻译key
     * @return 翻译对象
     */
    I18nTranslation getByKey(String key);

    /**
     * 根据模块获取所有翻译
     *
     * @param module 模块名称
     * @return 翻译列表
     */
    List<I18nTranslation> getByModule(String module);

    /**
     * 保存或更新翻译
     *
     * @param translation 翻译对象
     * @return 是否成功
     */
    Boolean saveOrUpdateTranslation(I18nTranslation translation);

    /**
     * 批量导入翻译
     *
     * @param translations 翻译列表
     * @return 导入成功数量
     */
    Integer batchImport(List<I18nTranslation> translations);

    /**
     * 导出翻译（Excel）
     *
     * @param module 模块（可选）
     * @return 翻译数据
     */
    List<I18nTranslation> exportTranslations(String module);

    /**
     * AI自动翻译（调用翻译API）
     *
     * @param key    翻译key
     * @param zhText 中文文本
     * @return 是否成功
     */
    Boolean autoTranslate(String key, String zhText);

    /**
     * 清除翻译缓存
     */
    void clearCache();
}

