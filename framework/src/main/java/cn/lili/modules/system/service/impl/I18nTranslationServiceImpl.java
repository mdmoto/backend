package cn.lili.modules.system.service.impl;

import cn.lili.modules.system.entity.dos.I18nTranslation;
import cn.lili.modules.system.mapper.I18nTranslationMapper;
import cn.lili.modules.system.service.I18nTranslationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多语言翻译Service实现
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Slf4j
@Service
public class I18nTranslationServiceImpl extends ServiceImpl<I18nTranslationMapper, I18nTranslation> 
    implements I18nTranslationService {

    @Override
    public I18nTranslation getByKey(String key) {
        return this.baseMapper.selectByKey(key);
    }

    @Override
    public List<I18nTranslation> getByModule(String module) {
        return this.baseMapper.selectByModule(module);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"i18n", "i18n_obj"}, allEntries = true)
    public Boolean saveOrUpdateTranslation(I18nTranslation translation) {
        try {
            // 检查key是否已存在
            I18nTranslation existing = getByKey(translation.getTranslationKey());
            
            if (existing != null) {
                // 更新
                translation.setId(existing.getId());
                return this.updateById(translation);
            } else {
                // 新增
                return this.save(translation);
            }
        } catch (Exception e) {
            log.error("保存翻译失败: {}", translation.getTranslationKey(), e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"i18n", "i18n_obj"}, allEntries = true)
    public Integer batchImport(List<I18nTranslation> translations) {
        int successCount = 0;
        
        for (I18nTranslation translation : translations) {
            if (saveOrUpdateTranslation(translation)) {
                successCount++;
            }
        }
        
        log.info("批量导入翻译完成: 成功{}/总数{}", successCount, translations.size());
        return successCount;
    }

    @Override
    public List<I18nTranslation> exportTranslations(String module) {
        LambdaQueryWrapper<I18nTranslation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(I18nTranslation::getDeleteFlag, false);
        
        if (module != null && !module.trim().isEmpty()) {
            queryWrapper.eq(I18nTranslation::getModule, module);
        }
        
        queryWrapper.orderByAsc(I18nTranslation::getModule, I18nTranslation::getTranslationKey);
        
        return this.list(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"i18n", "i18n_obj"}, allEntries = true)
    public Boolean autoTranslate(String key, String zhText) {
        // TODO: 集成翻译API（如Google Translate, DeepL等）
        // 这里先返回false，后续可以添加AI翻译功能
        log.warn("AI自动翻译功能待实现: key={}, text={}", key, zhText);
        return false;
    }

    @Override
    @CacheEvict(value = {"i18n", "i18n_obj"}, allEntries = true)
    public void clearCache() {
        log.info("清除所有i18n缓存");
    }
}

