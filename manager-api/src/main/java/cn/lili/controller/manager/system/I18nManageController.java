package cn.lili.controller.manager.system;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.system.entity.dos.I18nTranslation;
import cn.lili.modules.system.service.I18nTranslationService;
import cn.lili.mybatis.util.PageUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 多语言翻译管理Controller
 *
 * @author Chopper
 * @since 2025-11-05
 */
@Slf4j
@RestController
@Api(tags = "管理后台-多语言翻译管理")
@RequestMapping("/manager/system/i18n")
public class I18nManageController {

    @Autowired
    private I18nTranslationService i18nTranslationService;

    @ApiOperation(value = "分页查询翻译列表")
    @GetMapping("/list")
    public ResultMessage<IPage<I18nTranslation>> getList(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String keyword,
            PageVO page) {
        LambdaQueryWrapper<I18nTranslation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(I18nTranslation::getDeleteFlag, false);

        if (StringUtils.hasText(module)) {
            queryWrapper.eq(I18nTranslation::getModule, module.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(I18nTranslation::getTranslationKey, kw)
                    .or().like(I18nTranslation::getZhCn, kw)
                    .or().like(I18nTranslation::getEnUs, kw)
                    .or().like(I18nTranslation::getDescription, kw));
        }

        IPage<I18nTranslation> result = i18nTranslationService.page(PageUtil.initPage(page), queryWrapper);
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "获取单个翻译")
    @GetMapping("/{id}")
    public ResultMessage<I18nTranslation> getById(@PathVariable String id) {
        I18nTranslation translation = i18nTranslationService.getById(id);
        if (translation == null) {
            throw new ServiceException(ResultCode.ERROR, "翻译不存在");
        }
        return ResultUtil.data(translation);
    }

    @ApiOperation(value = "新增翻译")
    @PostMapping
    public ResultMessage<Boolean> save(@Valid @RequestBody I18nTranslation translation) {
        // 检查权限
        if (!UserContext.getCurrentUser().getIsSuper()) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        
        Boolean result = i18nTranslationService.saveOrUpdateTranslation(translation);
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "更新翻译")
    @PutMapping("/{id}")
    public ResultMessage<Boolean> update(@PathVariable String id, @Valid @RequestBody I18nTranslation translation) {
        // 检查权限
        if (!UserContext.getCurrentUser().getIsSuper()) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        
        translation.setId(id);
        Boolean result = i18nTranslationService.saveOrUpdateTranslation(translation);
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "删除翻译")
    @DeleteMapping("/{id}")
    public ResultMessage<Boolean> delete(@PathVariable String id) {
        // 检查权限
        if (!UserContext.getCurrentUser().getIsSuper()) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        
        // 软删除
        I18nTranslation translation = i18nTranslationService.getById(id);
        if (translation == null) {
            throw new ServiceException(ResultCode.ERROR, "翻译不存在");
        }
        
        // 检查是否是系统预置（系统预置不能删除）
        if (translation.getIsSystem()) {
            throw new ServiceException("系统预置翻译不能删除，只能修改");
        }
        
        translation.setDeleteFlag(true);
        Boolean result = i18nTranslationService.updateById(translation);
        
        // 清除缓存
        i18nTranslationService.clearCache();
        
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "批量导入翻译")
    @PostMapping("/batch-import")
    public ResultMessage<Integer> batchImport(@RequestBody List<I18nTranslation> translations) {
        // 检查权限
        if (!UserContext.getCurrentUser().getIsSuper()) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        
        Integer successCount = i18nTranslationService.batchImport(translations);
        return ResultUtil.data(successCount);
    }

    @ApiOperation(value = "导出翻译")
    @GetMapping("/export")
    public ResultMessage<List<I18nTranslation>> export(
            @RequestParam(required = false) String module) {
        
        List<I18nTranslation> translations = i18nTranslationService.exportTranslations(module);
        return ResultUtil.data(translations);
    }

    @ApiOperation(value = "清除翻译缓存")
    @PostMapping("/clear-cache")
    public ResultMessage<Boolean> clearCache() {
        // 检查权限
        if (!UserContext.getCurrentUser().getIsSuper()) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        
        i18nTranslationService.clearCache();
        return ResultUtil.success();
    }

    @ApiOperation(value = "AI自动翻译")
    @PostMapping("/auto-translate")
    public ResultMessage<Boolean> autoTranslate(
            @RequestParam String key,
            @RequestParam String zhText) {
        
        // 检查权限
        if (!UserContext.getCurrentUser().getIsSuper()) {
            throw new ServiceException(ResultCode.USER_AUTHORITY_ERROR);
        }
        
        Boolean result = i18nTranslationService.autoTranslate(key, zhText);
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "获取支持的语言列表")
    @GetMapping("/languages")
    public ResultMessage<List<Map<String, String>>> getLanguages() {
        List<Map<String, String>> languages = List.of(
            Map.of("code", "zh-CN", "name", "简体中文", "flag", "🇨🇳"),
            Map.of("code", "en-US", "name", "English", "flag", "🇺🇸"),
            Map.of("code", "ja-JP", "name", "日本語", "flag", "🇯🇵"),
            Map.of("code", "ko-KR", "name", "한국어", "flag", "🇰🇷"),
            Map.of("code", "th-TH", "name", "ภาษาไทย", "flag", "🇹🇭"),
            Map.of("code", "es-ES", "name", "Español", "flag", "🇪🇸"),
            Map.of("code", "fr-FR", "name", "Français", "flag", "🇫🇷"),
            Map.of("code", "vi-VN", "name", "Tiếng Việt", "flag", "🇻🇳")
        );
        return ResultUtil.data(languages);
    }
}

