package cn.lili.modules.page.serviceimpl;

import cn.lili.modules.goods.service.GoodsSkuService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageDataServiceImplTest {

    @Test
    void filtersMissingSkuFromObjectRootUsedByProductionPages() throws Exception {
        PageDataServiceImpl service = serviceWithNoMatchingSkus();
        String source = "{\"list\":[{\"options\":{\"items\":[{\"url\":\"/pages/product/goods?id=missing-sku\"}]}}]}";

        String filtered = invokeFilter(service, source);

        assertEquals(0, cn.hutool.json.JSONUtil.parseObj(filtered)
                .getJSONArray("list").getJSONObject(0)
                .getJSONObject("options").getJSONArray("items").size());
    }

    @Test
    void keepsSupportingLegacyArrayRoot() throws Exception {
        PageDataServiceImpl service = serviceWithNoMatchingSkus();
        String source = "[{\"url\":\"/pages/product/goods?id=missing-sku\"}]";

        String filtered = invokeFilter(service, source);

        assertEquals(0, cn.hutool.json.JSONUtil.parseArray(filtered).size());
    }

    @Test
    void filtersMissingSkuFromPcGoodsDetailUrl() throws Exception {
        PageDataServiceImpl service = serviceWithNoMatchingSkus();
        String source = "{\"items\":[{\"url\":\"/goodsDetail?skuId=missing-sku&goodsId=old-goods\"}]}";

        String filtered = invokeFilter(service, source);

        assertEquals(0, cn.hutool.json.JSONUtil.parseObj(filtered).getJSONArray("items").size());
    }

    @Test
    void filtersMissingSkuFromNestedH5GoodsPayload() throws Exception {
        PageDataServiceImpl service = serviceWithNoMatchingSkus();
        String source = "{\"items\":[{\"url\":{\"id\":\"missing-sku\",\"goodsId\":\"old-goods\",\"___type\":\"goods\"}}]}";

        String filtered = invokeFilter(service, source);

        assertEquals(0, cn.hutool.json.JSONUtil.parseObj(filtered).getJSONArray("items").size());
    }

    @Test
    void sanitizesMissingSkuLinkFromFixedObjectField() throws Exception {
        PageDataServiceImpl service = serviceWithNoMatchingSkus();
        String source = "{\"options\":{\"contentRight\":{\"title\":\"推荐\",\"url\":\"/goodsDetail?skuId=missing-sku&goodsId=old-goods\",\"list\":[]}}}";

        String filtered = invokeFilter(service, source);

        cn.hutool.json.JSONObject contentRight = cn.hutool.json.JSONUtil.parseObj(filtered)
                .getJSONObject("options").getJSONObject("contentRight");
        assertEquals("推荐", contentRight.getStr("title"));
        assertEquals("", contentRight.getStr("url"));
    }

    private PageDataServiceImpl serviceWithNoMatchingSkus() throws Exception {
        GoodsSkuService goodsSkuService = mock(GoodsSkuService.class);
        when(goodsSkuService.listByIds(anyList())).thenReturn(Collections.emptyList());

        PageDataServiceImpl service = new PageDataServiceImpl();
        Field field = PageDataServiceImpl.class.getDeclaredField("goodsSkuService");
        field.setAccessible(true);
        field.set(service, goodsSkuService);
        return service;
    }

    private String invokeFilter(PageDataServiceImpl service, String source) throws Exception {
        Method method = PageDataServiceImpl.class.getDeclaredMethod("filterPageData", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, source);
    }
}
