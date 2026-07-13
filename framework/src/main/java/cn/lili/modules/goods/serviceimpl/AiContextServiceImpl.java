package cn.lili.modules.goods.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.goods.entity.dos.Category;
import cn.lili.modules.goods.entity.dos.CategoryAiTemplate;
import cn.lili.modules.goods.entity.dos.Goods;
import cn.lili.modules.goods.entity.dos.GoodsAiContext;
import cn.lili.modules.goods.mapper.CategoryAiTemplateMapper;
import cn.lili.modules.goods.mapper.GoodsAiContextMapper;
import cn.lili.modules.goods.service.AiContextService;
import cn.lili.modules.goods.service.CategoryService;
import cn.lili.modules.goods.service.GoodsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 商品AI上下文业务层实现
 */
@Slf4j
@Service
public class AiContextServiceImpl implements AiContextService {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryAiTemplateMapper categoryAiTemplateMapper;

    @Autowired
    private GoodsAiContextMapper goodsAiContextMapper;

    @Override
    public void generateContext(String goodsId) {
        Goods goods = goodsService.getById(goodsId);
        if (goods == null) {
            return;
        }

        String contextJson = this.getContextJson(goods);
        if (contextJson == null) {
            return;
        }

        // 获取该商品分类对应的模版版本
        Long categoryId = getTerminalCategoryId(goods.getCategoryPath());
        CategoryAiTemplate template = getActiveTemplate(categoryId);
        Integer version = template != null ? template.getTemplateVersion() : 1;

        // 保存或更新快照
        GoodsAiContext context = goodsAiContextMapper.selectOne(
                new LambdaQueryWrapper<GoodsAiContext>().eq(GoodsAiContext::getGoodsId, goodsId)
        );

        if (context == null) {
            context = new GoodsAiContext();
            context.setGoodsId(goodsId);
            context.setCreateTime(new Date());
        }
        context.setCategoryId(categoryId);
        context.setTemplateVersion(version);
        context.setAiContext(contextJson);
        context.setNeedsRefresh(false);
        context.setUpdateTime(new Date());

        if (context.getId() == null) {
            goodsAiContextMapper.insert(context);
        } else {
            goodsAiContextMapper.updateById(context);
        }
    }

    @Override
    public String getContextJson(Goods goods) {
        try {
            Long categoryId = getTerminalCategoryId(goods.getCategoryPath());
            Category category = categoryService.getById(categoryId);
            CategoryAiTemplate template = getActiveTemplate(categoryId);

            JSONObject root = JSONUtil.createObj();
            root.set("schema_version", "1.0");

            // 1. 分类信息
            JSONObject categoryObj = JSONUtil.createObj();
            categoryObj.set("id", categoryId);
            categoryObj.set("name", category != null ? category.getName() : "Unknown");
            if (category != null) {
                categoryObj.set("slug", category.getSlug());
                categoryObj.set("ai_hint", template != null ? template.getAiHint() : category.getAiDescription());
            }
            root.set("category", categoryObj);

            // 2. 商品基础信息
            JSONObject productObj = JSONUtil.createObj();
            productObj.set("id", goods.getId());
            productObj.set("title", goods.getGoodsName());
            productObj.set("price_usd", goods.getPriceUsd());
            productObj.set("brand_id", goods.getBrandId());
            productObj.set("selling_point", goods.getSellingPoint());
            root.set("product", productObj);

            // 3. 参数对齐 (核心逻辑)
            JSONObject specs = JSONUtil.createObj();
            if (CharSequenceUtil.isNotEmpty(goods.getParams())) {
                JSONArray paramsArray = JSONUtil.parseArray(goods.getParams());
                Map<String, String> rawParams = extractRawParams(paramsArray);

                if (template != null && CharSequenceUtil.isNotEmpty(template.getSchemaJson())) {
                    JSONObject schema = JSONUtil.parseObj(template.getSchemaJson());
                    JSONObject properties = schema.getJSONObject("properties");
                    
                    // 按照模版定义的属性进行对齐
                    if (properties != null) {
                        for (String key : properties.keySet()) {
                            JSONObject propDef = properties.getJSONObject(key);
                            String aiLabel = propDef.getStr("ai_label");
                            String unit = propDef.getStr("unit");
                            
                            // 在原始参数中寻找匹配项 (精确匹配或包含匹配)
                            String value = findValue(rawParams, aiLabel, key);
                            if (value != null) {
                                if (CharSequenceUtil.isNotEmpty(unit)) {
                                    JSONObject valObj = JSONUtil.createObj();
                                    valObj.set("value", tryParseNumber(value));
                                    valObj.set("unit", unit);
                                    specs.set(key, valObj);
                                } else {
                                    specs.set(key, value);
                                }
                            }
                        }
                    }
                } else {
                    // 如果没有模版，直接把所有原始参数塞进去
                    rawParams.forEach(specs::set);
                }
            }
            root.set("specs", specs);
            
            // 4. 对比建议
            if (template != null) {
                root.set("comparison_keys", template.getComparisonKeys());
            }

            root.set("generated_at", new Date());
            return JSONUtil.toJsonPrettyStr(root);
        } catch (Exception e) {
            log.error("生成商品AI上下文失败: goodsId={}", goods.getId(), e);
            return null;
        }
    }

    private Long getTerminalCategoryId(String categoryPath) {
        if (CharSequenceUtil.isEmpty(categoryPath)) return 0L;
        String[] ids = categoryPath.split(",");
        return Long.parseLong(ids[ids.length - 1]);
    }

    private CategoryAiTemplate getActiveTemplate(Long categoryId) {
        return categoryAiTemplateMapper.selectOne(
                new LambdaQueryWrapper<CategoryAiTemplate>()
                        .eq(CategoryAiTemplate::getCategoryId, categoryId)
                        .eq(CategoryAiTemplate::getIsActive, true)
                        .last("LIMIT 1")
        );
    }

    private Map<String, String> extractRawParams(JSONArray paramsGroups) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < paramsGroups.size(); i++) {
            JSONObject group = paramsGroups.getJSONObject(i);
            JSONArray params = group.getJSONArray("goodsParamsDTOList");
            if (params != null) {
                for (int j = 0; j < params.size(); j++) {
                    JSONObject p = params.getJSONObject(j);
                    result.put(p.getStr("paramName"), p.getStr("paramValue"));
                }
            }
        }
        return result;
    }

    private String findValue(Map<String, String> rawParams, String aiLabel, String key) {
        if (rawParams.containsKey(aiLabel)) return rawParams.get(aiLabel);
        if (rawParams.containsKey(key)) return rawParams.get(key);
        
        // 模糊匹配：如果参数名包含 aiLabel，比如 "存储容量" 包含 "存储"
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            if (entry.getKey().contains(aiLabel) || aiLabel.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Object tryParseNumber(String val) {
        try {
            // 去除单位后缀，只保留数字
            String numeric = val.replaceAll("[^0-9.]", "");
            if (CharSequenceUtil.isEmpty(numeric)) return val;
            if (numeric.contains(".")) return Double.parseDouble(numeric);
            return Long.parseLong(numeric);
        } catch (Exception e) {
            return val;
        }
    }
}
