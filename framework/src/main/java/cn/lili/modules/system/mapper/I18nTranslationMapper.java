package cn.lili.modules.system.mapper;

import cn.lili.modules.system.entity.dos.I18nTranslation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 多语言翻译Mapper
 *
 * @author Chopper
 * @since 2025-11-05
 */
public interface I18nTranslationMapper extends BaseMapper<I18nTranslation> {

    /**
     * 根据模块查询翻译
     *
     * @param module 模块名称
     * @return 翻译列表
     */
    @Select("SELECT * FROM li_i18n_translation WHERE module = #{module} AND delete_flag = 0")
    List<I18nTranslation> selectByModule(@Param("module") String module);

    /**
     * 根据翻译key查询
     *
     * @param key 翻译key
     * @return 翻译对象
     */
    @Select("SELECT * FROM li_i18n_translation WHERE translation_key = #{key} AND delete_flag = 0 LIMIT 1")
    I18nTranslation selectByKey(@Param("key") String key);

    /**
     * 查询所有系统预置翻译
     *
     * @return 翻译列表
     */
    @Select("SELECT * FROM li_i18n_translation WHERE is_system = 1 AND delete_flag = 0")
    List<I18nTranslation> selectSystemTranslations();
}

