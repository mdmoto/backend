package cn.lili.modules.page.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.common.enums.ClientTypeEnum;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.SwitchEnum;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.properties.SystemSettingProperties;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.page.entity.dos.PageData;
import cn.lili.modules.page.entity.dto.PageDataDTO;
import cn.lili.modules.page.entity.enums.PageEnum;
import cn.lili.modules.page.entity.vos.PageDataListVO;
import cn.lili.modules.page.entity.vos.PageDataVO;
import cn.lili.modules.page.mapper.PageDataMapper;
import cn.lili.modules.page.service.PageDataService;
import cn.lili.mybatis.util.PageUtil;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.goods.entity.dos.GoodsSku;
import cn.lili.modules.goods.entity.enums.GoodsAuthEnum;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import cn.lili.modules.goods.service.GoodsSkuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 楼层装修管理业务层实现
 *
 * @author Bulbasaur
 * @since 2020/12/11 9:15
 */
@Service
@Slf4j
public class PageDataServiceImpl extends ServiceImpl<PageDataMapper, PageData> implements PageDataService {

    private static final Pattern SKU_ID_QUERY_PATTERN = Pattern.compile("(?:[?&])(?:skuId|id)=([^&#]+)");


    @Autowired
    private SystemSettingProperties systemSettingProperties;

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStorePageData(String storeId) {
        //设置店铺的PC页面
        PageData pageData = new PageData();
        pageData.setNum(storeId);
        pageData.setPageClientType(ClientTypeEnum.PC.value());
        pageData.setPageShow(SwitchEnum.OPEN.name());
        pageData.setPageType(PageEnum.STORE.value());
        this.save(pageData);

        //设置店铺的Mobile页面
        PageData mobilePageData = new PageData();
        mobilePageData.setNum(storeId);
        mobilePageData.setPageClientType(ClientTypeEnum.H5.value());
        mobilePageData.setPageShow(SwitchEnum.OPEN.name());
        mobilePageData.setPageType(PageEnum.STORE.value());
        this.save(mobilePageData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageData addPageData(PageData pageData) {
        AuthUser currentUser = Objects.requireNonNull(UserContext.getCurrentUser());
        //演示站点判定
        if (Boolean.TRUE.equals(systemSettingProperties.getDemoSite()) && (pageData.getPageShow().equals(SwitchEnum.OPEN.name()) && pageData.getPageType().equals(PageEnum.INDEX.name()))) {
                pageData.setPageShow(SwitchEnum.CLOSE.name());

        }

        //如果页面为发布，则关闭其他页面，开启此页面
        if (pageData.getPageShow().equals(SwitchEnum.OPEN.name())) {
            LambdaUpdateWrapper<PageData> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
            lambdaUpdateWrapper.eq(CharSequenceUtil.equals(currentUser.getRole().name(), UserEnums.STORE.name()), PageData::getNum
                    , UserContext.getCurrentUser().getStoreId());
            lambdaUpdateWrapper.eq(PageData::getPageType, pageData.getPageType());
            lambdaUpdateWrapper.eq(PageData::getPageClientType, pageData.getPageClientType());
            lambdaUpdateWrapper.set(PageData::getPageShow, SwitchEnum.CLOSE.name());
            this.update(lambdaUpdateWrapper);
        } else {
            pageData.setPageShow(SwitchEnum.CLOSE.name());
        }
        this.save(pageData);
        return pageData;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageData updatePageData(PageData pageData) {
        AuthUser currentUser = Objects.requireNonNull(UserContext.getCurrentUser());
        //如果页面为发布，则关闭其他页面，开启此页面
        if (pageData.getPageShow() != null && pageData.getPageShow().equals(SwitchEnum.OPEN.name())) {
            LambdaUpdateWrapper<PageData> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
            lambdaUpdateWrapper.eq(CharSequenceUtil.isNotEmpty(pageData.getPageType()), PageData::getPageType, pageData.getPageType());
            lambdaUpdateWrapper.eq(CharSequenceUtil.isNotEmpty(pageData.getPageClientType()), PageData::getPageClientType,
                    pageData.getPageClientType());

            //如果是管理员，则判定页面num为null
            if (currentUser.getRole().name().equals(UserEnums.MANAGER.name())) {
                lambdaUpdateWrapper.isNull(PageData::getNum);
            } else {
                lambdaUpdateWrapper.eq(PageData::getNum, pageData.getNum());
            }

            lambdaUpdateWrapper.set(PageData::getPageShow, SwitchEnum.CLOSE.name());
            this.update(lambdaUpdateWrapper);
        } else {
            pageData.setPageShow(SwitchEnum.CLOSE.name());
        }

        LambdaUpdateWrapper<PageData> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        lambdaUpdateWrapper.set(PageData::getPageData, pageData.getPageData());
        lambdaUpdateWrapper.eq(PageData::getId, pageData.getId());
        lambdaUpdateWrapper.eq(CharSequenceUtil.equals(UserContext.getCurrentUser().getRole().name(), UserEnums.STORE.name()),
                PageData::getPageType, PageEnum.STORE.name());
        lambdaUpdateWrapper.eq(CharSequenceUtil.equals(UserContext.getCurrentUser().getRole().name(), UserEnums.STORE.name()), PageData::getNum,
                UserContext.getCurrentUser().getStoreId());
        this.updateById(pageData);
        return pageData;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageData releasePageData(String id) {
        PageData pageData = this.getCurrentPageData(id);
        if (pageData == null) {
            throw new ServiceException(ResultCode.PAGE_NOT_EXIST);
        }

        //如果已经发布，不能重复发布
        if (pageData.getPageShow().equals(SwitchEnum.OPEN.name())) {
            throw new ServiceException(ResultCode.PAGE_RELEASE_ERROR);
        }

        LambdaUpdateWrapper<PageData> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        lambdaUpdateWrapper.set(PageData::getPageShow, SwitchEnum.CLOSE.name());
        lambdaUpdateWrapper.eq(PageData::getPageType, pageData.getPageType());
        lambdaUpdateWrapper.eq(PageData::getPageClientType, pageData.getPageClientType());
        //如果是店铺需要设置店铺ID
        if (pageData.getPageType().equals(PageEnum.STORE.value())) {
            lambdaUpdateWrapper.eq(PageData::getNum, pageData.getNum());
        }
        //设置禁用所有店铺首页
        this.update(lambdaUpdateWrapper);

        //将当前页面启用
        LambdaUpdateWrapper<PageData> wrapper = Wrappers.lambdaUpdate();
        wrapper.set(PageData::getPageShow, SwitchEnum.OPEN.name());
        wrapper.eq(PageData::getId, pageData.getId());
        this.update(wrapper);
        return pageData;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePageData(String id) {
        PageData pageData = this.getCurrentPageData(id);
        if (pageData == null) {
            throw new ServiceException(ResultCode.PAGE_NOT_EXIST);
        }

        //专题则直接进行删除
        if (pageData.getPageType().equals(PageEnum.SPECIAL.name())) {
            return this.removeById(id);
        }
        //店铺、平台首页需要判断是否开启，开启则无法删除
        if (pageData.getPageShow().equals(SwitchEnum.OPEN.name())) {
            throw new ServiceException(ResultCode.PAGE_OPEN_DELETE_ERROR);
        }
        //判断是否有其他页面，如果没有其他的页面则无法进行删除
        LambdaQueryWrapper<PageData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PageData::getPageType, pageData.getPageType());
        queryWrapper.eq(pageData.getPageClientType() != null, PageData::getPageClientType, pageData.getPageClientType());
        //如果为店铺页面需要设置店铺ID
        if (pageData.getPageType().equals(PageEnum.STORE.name())) {
            queryWrapper.eq(pageData.getNum() != null, PageData::getNum, pageData.getNum());
        }
        //判断是否为唯一的页面
        if (this.baseMapper.getPageDataNum(queryWrapper) == 1) {
            throw new ServiceException(ResultCode.PAGE_DELETE_ERROR);
        }
        return this.removeById(id);
    }

    @Override
    public PageDataVO getPageData(PageDataDTO pageDataDTO) {

        //如果获取的是专题、店铺页面数据需要传入ID
        if (!pageDataDTO.getPageType().equals(PageEnum.INDEX.name()) && pageDataDTO.getNum() == null) {
            throw new ServiceException(ResultCode.PAGE_NOT_EXIST);
        }
        QueryWrapper<PageDataVO> queryWrapper = Wrappers.query();
        queryWrapper.eq("page_type", pageDataDTO.getPageType());
        queryWrapper.eq(pageDataDTO.getNum() != null, "num", pageDataDTO.getNum());
        queryWrapper.eq("page_show", SwitchEnum.OPEN.name());

        queryWrapper.eq("page_client_type", pageDataDTO.getPageClientType());

        PageDataVO pageDataVO = this.baseMapper.getPageData(queryWrapper);
        if (pageDataVO != null && CharSequenceUtil.isNotEmpty(pageDataVO.getPageData())) {
            pageDataVO.setPageData(filterPageData(pageDataVO.getPageData()));
        }
        return pageDataVO;
    }

    /**
     * 过滤页面数据，移除已下架或异常商品
     *
     * @param pageDataJson 页面JSON数据
     * @return 过滤后的JSON
     */
    private String filterPageData(String pageDataJson) {
        try {
            Object components = parsePageComponents(pageDataJson);
            List<String> skuIds = new ArrayList<>();

            // 1. 递归收集所有包含商品链接的 skuId
            collectSkuIds(components, skuIds);

            if (skuIds.isEmpty()) {
                return pageDataJson;
            }

            // 2. 批量获取数据库中的商品状态。首页过滤不能只信任长期缓存，否则数据库已删除、
            // 缓存尚未淘汰的 SKU 仍会生成可点击的“幽灵商品”。
            List<GoodsSku> skus = goodsSkuService.listByIds(skuIds);
            Map<String, GoodsSku> skuMap = skus.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(GoodsSku::getId, s -> s));

            // 3. 递归过滤
            filterComponents(components, skuMap);

            return components.toString();
        } catch (Exception e) {
            log.error("过滤首页装修数据异常", e);
            return pageDataJson;
        }
    }

    /**
     * 页面装修历史数据的根节点既可能是对象（当前 PC/H5 数据），也可能是数组。
     */
    private Object parsePageComponents(String pageDataJson) {
        String trimmed = pageDataJson == null ? "" : pageDataJson.trim();
        if (trimmed.startsWith("{")) {
            return JSONUtil.parseObj(trimmed);
        }
        if (trimmed.startsWith("[")) {
            return JSONUtil.parseArray(trimmed);
        }
        throw new IllegalArgumentException("页面装修数据必须是 JSON 对象或数组");
    }

    private void collectSkuIds(Object obj, List<String> skuIds) {
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (Object item : array) {
                collectSkuIds(item, skuIds);
            }
        } else if (obj instanceof JSONObject) {
            JSONObject json = (JSONObject) obj;
            String skuId = resolveSkuId(json);
            if (CharSequenceUtil.isNotEmpty(skuId) && !skuIds.contains(skuId)) {
                skuIds.add(skuId);
            }
            // 递归遍历所有值
            for (Object value : json.values()) {
                collectSkuIds(value, skuIds);
            }
        }
    }

    private void filterComponents(Object obj, Map<String, GoodsSku> skuMap) {
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    JSONObject json = (JSONObject) item;
                    if (shouldRemove(json, skuMap)) {
                        array.remove(i);
                        i--;
                    } else {
                        filterComponents(item, skuMap);
                    }
                } else {
                    filterComponents(item, skuMap);
                }
            }
        } else if (obj instanceof JSONObject) {
            JSONObject json = (JSONObject) obj;
            // 某些 PC 装修组件把“更多”链接放在固定的对象字段里，不能删除整个对象，
            // 否则前端仍会读取标题、颜色和列表而报错；只清空已失效的跳转链接。
            if (shouldRemove(json, skuMap) && json.get("url") instanceof CharSequence) {
                json.set("url", "");
            }
            for (String key : json.keySet()) {
                Object value = json.get(key);
                if (value instanceof JSONArray) {
                    JSONArray subArray = (JSONArray) value;
                    for (int i = 0; i < subArray.size(); i++) {
                        Object subItem = subArray.get(i);
                        if (subItem instanceof JSONObject && shouldRemove((JSONObject) subItem, skuMap)) {
                            subArray.remove(i);
                            i--;
                        } else {
                            filterComponents(subItem, skuMap);
                        }
                    }
                } else {
                    filterComponents(value, skuMap);
                }
            }
        }
    }

    private boolean shouldRemove(JSONObject json, Map<String, GoodsSku> skuMap) {
        String skuId = resolveSkuId(json);

        if (CharSequenceUtil.isNotEmpty(skuId)) {
            GoodsSku sku = skuMap.get(skuId);
            if (sku == null) return true; // 缓存找不到，可能已彻底删除
            
            // 过滤条件：已下架、未审核通过、已删除
            return !GoodsStatusEnum.UPPER.name().equals(sku.getMarketEnable())
                    || !GoodsAuthEnum.PASS.name().equals(sku.getAuthFlag())
                    || Boolean.TRUE.equals(sku.getDeleteFlag());
        }
        return false;
    }

    /**
     * 兼容页面装修中三种商品引用：PC 的 skuId 查询参数、旧 H5 的 id 查询参数，
     * 以及 H5 把完整商品对象放进 url 字段的历史结构。
     */
    private String resolveSkuId(JSONObject json) {
        String skuId = json.getStr("skuId");
        if (CharSequenceUtil.isNotEmpty(skuId)) {
            return skuId;
        }

        if (json.containsKey("goodsId") || "goods".equals(json.getStr("___type"))) {
            String embeddedSkuId = json.getStr("id");
            if (CharSequenceUtil.isNotEmpty(embeddedSkuId)) {
                return embeddedSkuId;
            }
        }

        Object url = json.get("url");
        if (url instanceof JSONObject) {
            return resolveSkuId((JSONObject) url);
        }
        if (url instanceof CharSequence) {
            return extractSkuId(url.toString());
        }
        return null;
    }

    private String extractSkuId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = SKU_ID_QUERY_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public IPage<PageDataListVO> getPageDataList(PageVO pageVO, PageDataDTO pageDataDTO) {
        QueryWrapper<PageDataListVO> queryWrapper = Wrappers.query();
        queryWrapper.eq(pageDataDTO.getPageType() != null, "page_type", pageDataDTO.getPageType());
        queryWrapper.eq(pageDataDTO.getNum() != null, "num", pageDataDTO.getNum());
        queryWrapper.eq(pageDataDTO.getPageClientType() != null, "page_client_type", pageDataDTO.getPageClientType());

        return this.baseMapper.getPageDataList(PageUtil.initPage(pageVO), queryWrapper);

    }

    @Override
    public PageData getSpecial(String id) {
        return this.getById(id);
    }

    private PageData getCurrentPageData(String id) {
        AuthUser currentUser = Objects.requireNonNull(UserContext.getCurrentUser());
        return this.getOne(new LambdaQueryWrapper<PageData>()
                .eq(CharSequenceUtil.equals(currentUser.getRole().name(), UserEnums.STORE.name()), PageData::getPageType, PageEnum.STORE.name())
                .eq(CharSequenceUtil.equals(currentUser.getRole().name(), UserEnums.STORE.name()), PageData::getNum, UserContext.getCurrentUser().getStoreId())
                .eq(PageData::getId, id), false);

    }
}
