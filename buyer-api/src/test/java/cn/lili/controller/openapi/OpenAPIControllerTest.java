package cn.lili.controller.openapi;

import cn.hutool.json.JSONUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.openapi.entity.OpenApiKey;
import cn.lili.modules.openapi.interceptor.OpenApiAuthInterceptor;
import cn.lili.modules.openapi.service.AIService;
import cn.lili.modules.openapi.service.OpenApiKeyService;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import cn.lili.modules.order.cart.entity.vo.TradeParams;
import cn.lili.modules.order.cart.service.CartService;
import cn.lili.modules.order.order.entity.dos.Trade;
import cn.lili.modules.search.entity.dos.EsGoodsIndex;
import cn.lili.modules.search.entity.dto.EsGoodsSearchDTO;
import cn.lili.modules.search.service.EsGoodsSearchService;
import cn.lili.modules.wallet.entity.dto.MemberWalletUpdateDTO;
import cn.lili.modules.wallet.entity.vo.MemberWalletVO;
import cn.lili.modules.wallet.service.MemberWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class OpenAPIControllerTest {

    private MockMvc mockMvc;

    private OpenAPIController openAPIController;
    private OpenApiAuthInterceptor authInterceptor;

    // Stubs
    private StubEsGoodsSearchService esGoodsSearchService;
    private StubAIService aiService;
    private StubCartService cartService;
    private StubMemberWalletService memberWalletService;
    private StubOpenApiKeyService openApiKeyService;

    @BeforeEach
    public void setup() {
        openAPIController = new OpenAPIController();
        authInterceptor = new OpenApiAuthInterceptor();

        esGoodsSearchService = new StubEsGoodsSearchService();
        aiService = new StubAIService();
        cartService = new StubCartService();
        memberWalletService = new StubMemberWalletService();
        openApiKeyService = new StubOpenApiKeyService();

        ReflectionTestUtils.setField(openAPIController, "esGoodsSearchService", esGoodsSearchService);
        ReflectionTestUtils.setField(openAPIController, "aiService", aiService);
        ReflectionTestUtils.setField(openAPIController, "cartService", cartService);
        ReflectionTestUtils.setField(openAPIController, "memberWalletService", memberWalletService);
        
        ReflectionTestUtils.setField(authInterceptor, "openApiKeyService", openApiKeyService);

        mockMvc = MockMvcBuilders.standaloneSetup(openAPIController)
                .addMappedInterceptors(
                        new String[]{"/buyer/openapi/**"},
                        authInterceptor
                )
                .build();
    }

    private void setupValidAuth() {
        OpenApiKey key = new OpenApiKey();
        key.setApiKey("test-key");
        key.setApiSecret("test-secret");
        key.setStatus("OPEN");
        key.setMemberId("member123");
        key.setPermissions("Read:Product,Write:Order");
        openApiKeyService.setKeyToReturn(key);
    }

    @Test
    public void testAuthInterceptorFail() throws Exception {
        mockMvc.perform(get("/buyer/openapi/product/search"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testAuthInterceptorPermissionDenied() throws Exception {
        OpenApiKey key = new OpenApiKey();
        key.setApiKey("test-key");
        key.setApiSecret("test-secret");
        key.setStatus("OPEN");
        key.setPermissions("Read:Product"); // Missing Write:Order
        openApiKeyService.setKeyToReturn(key);

        Map<String, Object> payload = new HashMap<>();
        payload.put("skuId", "sku123");

        mockMvc.perform(post("/buyer/openapi/order/create")
                .header("API-KEY", "test-key")
                .header("API-SECRET", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONUtil.toJsonStr(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testProductSearchWithAISummary() throws Exception {
        setupValidAuth();

        EsGoodsIndex mockGoods = new EsGoodsIndex();
        mockGoods.setGoodsId("g1");
        mockGoods.setId("sku1");
        mockGoods.setGoodsName("Test Goods");
        mockGoods.setPrice(100.0);

        SearchHit<EsGoodsIndex> mockHit = new SearchHit<>(
            "sku1", "sku1", null, 1.0f, null, null, null, null, null, null, mockGoods
        );

        StubSearchPage<EsGoodsIndex> mockPage = new StubSearchPage<>(Collections.singletonList(mockHit), 1L, 1);
        esGoodsSearchService.setPageToReturn(mockPage);
        aiService.setSummaryToReturn("High yield test");

        mockMvc.perform(get("/buyer/openapi/product/search")
                .header("API-KEY", "test-key")
                .header("API-SECRET", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].ai_summary").value("High yield test"))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    public void testCreateOrderInsufficientBalanceReturns403() throws Exception {
        setupValidAuth();

        Trade trade = new Trade();
        trade.setSn("TR123456");
        trade.setFlowPrice(200.0);
        cartService.setTradeToReturn(trade);

        MemberWalletVO wallet = new MemberWalletVO();
        wallet.setMemberWallet(50.0); // Less than 200.0
        memberWalletService.setWalletToReturn(wallet);

        Map<String, Object> payload = new HashMap<>();
        payload.put("skuId", "sku123");
        payload.put("num", 2);

        mockMvc.perform(post("/buyer/openapi/order/create")
                .header("API-KEY", "test-key")
                .header("API-SECRET", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONUtil.toJsonStr(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden: Insufficient Prepaid Deposit (Member Wallet)"));
    }

    @Test
    public void testCreateOrderSuccess() throws Exception {
        setupValidAuth();

        Trade trade = new Trade();
        trade.setSn("TR123456");
        trade.setFlowPrice(100.0);
        cartService.setTradeToReturn(trade);

        MemberWalletVO wallet = new MemberWalletVO();
        wallet.setMemberWallet(500.0);
        memberWalletService.setWalletToReturn(wallet);
        memberWalletService.setReduceResult(true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("skuId", "sku123");
        payload.put("num", 2);

        mockMvc.perform(post("/buyer/openapi/order/create")
                .header("API-KEY", "test-key")
                .header("API-SECRET", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONUtil.toJsonStr(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("TR123456"));
    }

    // --- Lightweight Stubs to completely avoid Mockito ---

    static class StubOpenApiKeyService implements OpenApiKeyService {
        private OpenApiKey keyToReturn;

        public void setKeyToReturn(OpenApiKey key) {
            this.keyToReturn = key;
        }

        @Override
        public OpenApiKey getByApiKey(String apiKey) {
            return "test-key".equals(apiKey) ? keyToReturn : null;
        }

        @Override public cn.lili.modules.openapi.entity.OpenApiKey generateKey() { return null; }
        @Override public boolean save(cn.lili.modules.openapi.entity.OpenApiKey entity) { return false; }
        @Override public boolean saveBatch(java.util.Collection<cn.lili.modules.openapi.entity.OpenApiKey> entityList, int batchSize) { return false; }
        @Override public boolean saveOrUpdateBatch(java.util.Collection<cn.lili.modules.openapi.entity.OpenApiKey> entityList, int batchSize) { return false; }
        @Override public boolean removeById(java.io.Serializable id) { return false; }
        @Override public boolean removeByMap(java.util.Map<String, Object> columnMap) { return false; }
        @Override public boolean remove(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return false; }
        @Override public boolean removeByIds(java.util.Collection<? extends java.io.Serializable> idList) { return false; }
        @Override public boolean updateById(cn.lili.modules.openapi.entity.OpenApiKey entity) { return false; }
        @Override public boolean update(cn.lili.modules.openapi.entity.OpenApiKey entity, com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> updateWrapper) { return false; }
        @Override public boolean updateBatchById(java.util.Collection<cn.lili.modules.openapi.entity.OpenApiKey> entityList, int batchSize) { return false; }
        @Override public boolean saveOrUpdate(cn.lili.modules.openapi.entity.OpenApiKey entity) { return false; }
        @Override public cn.lili.modules.openapi.entity.OpenApiKey getById(java.io.Serializable id) { return null; }
        @Override public java.util.Collection<cn.lili.modules.openapi.entity.OpenApiKey> listByIds(java.util.Collection<? extends java.io.Serializable> idList) { return null; }
        @Override public java.util.Collection<cn.lili.modules.openapi.entity.OpenApiKey> listByMap(java.util.Map<String, Object> columnMap) { return null; }
        @Override public cn.lili.modules.openapi.entity.OpenApiKey getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper, boolean throwEx) { return keyToReturn; }
        @Override public java.util.Map<String, Object> getMap(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return null; }
        @Override public <V> V getObj(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper, java.util.function.Function<? super Object, V> mapper) { return null; }
        @Override public long count(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return 0; }
        @Override public java.util.List<cn.lili.modules.openapi.entity.OpenApiKey> list(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return null; }
        @Override public com.baomidou.mybatisplus.core.metadata.IPage<cn.lili.modules.openapi.entity.OpenApiKey> page(com.baomidou.mybatisplus.core.metadata.IPage<cn.lili.modules.openapi.entity.OpenApiKey> page, com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return null; }
        @Override public java.util.List<java.util.Map<String, Object>> listMaps(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return null; }
        @Override public <V> java.util.List<V> listObjs(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper, java.util.function.Function<? super Object, V> mapper) { return null; }
        @Override public com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> pageMaps(com.baomidou.mybatisplus.core.metadata.IPage<cn.lili.modules.openapi.entity.OpenApiKey> page, com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.openapi.entity.OpenApiKey> queryWrapper) { return null; }
        @Override public cn.lili.modules.openapi.entity.OpenApiKey getBaseMapper() { return null; }
    }

    static class StubEsGoodsSearchService implements EsGoodsSearchService {
        private SearchPage<EsGoodsIndex> pageToReturn;
        public void setPageToReturn(SearchPage<EsGoodsIndex> page) { this.pageToReturn = page; }
        @Override public SearchPage<EsGoodsIndex> searchGoods(EsGoodsSearchDTO goodsSearch, PageVO pageVo) { return pageToReturn; }
        @Override public java.util.List<String> getHotWords(Integer count) { return null; }
        @Override public void setHotWords(EsGoodsSearchDTO goodsSearch) {}
    }

    static class StubAIService implements AIService {
        private String summaryToReturn;
        public void setSummaryToReturn(String s) { this.summaryToReturn = s; }
        @Override public String generateGoodsSummary(String goodsName, Double price) { return summaryToReturn; }
    }

    static class StubCartService implements CartService {
        private Trade tradeToReturn;
        public void setTradeToReturn(Trade t) { this.tradeToReturn = t; }
        @Override public Trade createTrade(TradeParams tradeParams) { return tradeToReturn; }
        @Override public void add(String skuId, Integer num, String cartType, Boolean cover) {}
        @Override public cn.lili.modules.order.cart.entity.vo.TradeDTO readDTO(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) { return null; }
        @Override public void checkedAll(boolean checked, cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void checkedStore(String storeId, boolean checked, cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void checked(String skuId, boolean checked, cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void shippingAddress(String shippingAddressId, String way) {}
        @Override public void shippingMethod(String storeId, String deliveryMethod, String way) {}
        @Override public void shippingReceipt(cn.lili.modules.order.order.entity.vo.ReceiptVO receiptVO, String way) {}
        @Override public void shippingClient(String client, String way) {}
        @Override public void delete(String[] skuIds, cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void clean(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void cleanChecked(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void clearCart(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public void resetPrice(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) {}
        @Override public cn.lili.modules.order.cart.entity.vo.TradeDTO getAllTradeDTO(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) { return null; }
        @Override public cn.lili.modules.order.cart.entity.vo.TradeDTO getCheckedTradeDTO(cn.lili.modules.order.cart.entity.enums.CartTypeEnum cartTypeEnum) { return null; }
        @Override public Long getCartNum(Boolean checked) { return 0L; }
        @Override public void selectCoupon(String couponId, String way, boolean use) {}
    }

    static class StubMemberWalletService implements MemberWalletService {
        private MemberWalletVO walletToReturn;
        private Boolean reduceResult;
        public void setWalletToReturn(MemberWalletVO w) { this.walletToReturn = w; }
        public void setReduceResult(Boolean b) { this.reduceResult = b; }
        @Override public MemberWalletVO getMemberWallet(String memberId) { return walletToReturn; }
        @Override public Boolean reduce(MemberWalletUpdateDTO memberWalletUpdateDTO) { return reduceResult; }
        @Override public cn.lili.modules.wallet.entity.dos.MemberWallet getMemberWallet(String memberId, boolean forceUpdate) { return null; }
        @Override public Boolean increase(MemberWalletUpdateDTO memberWalletUpdateDTO) { return null; }
        @Override public Boolean reduceWithdraw(MemberWalletUpdateDTO memberWalletUpdateDTO) { return null; }
        @Override public void increaseWithdrawResult(String memberId, Double money, String detail) {}
        @Override public void setMemberWalletPassword(cn.lili.modules.member.entity.dto.MemberWalletUpdatePasswordDTO memberWalletUpdatePasswordDTO) {}
        @Override public Boolean checkPassword(String memberId, String password) { return null; }
        @Override public boolean save(cn.lili.modules.wallet.entity.dos.MemberWallet entity) { return false; }
        @Override public boolean saveBatch(java.util.Collection<cn.lili.modules.wallet.entity.dos.MemberWallet> entityList, int batchSize) { return false; }
        @Override public boolean saveOrUpdateBatch(java.util.Collection<cn.lili.modules.wallet.entity.dos.MemberWallet> entityList, int batchSize) { return false; }
        @Override public boolean removeById(java.io.Serializable id) { return false; }
        @Override public boolean removeByMap(java.util.Map<String, Object> columnMap) { return false; }
        @Override public boolean remove(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return false; }
        @Override public boolean removeByIds(java.util.Collection<? extends java.io.Serializable> idList) { return false; }
        @Override public boolean updateById(cn.lili.modules.wallet.entity.dos.MemberWallet entity) { return false; }
        @Override public boolean update(cn.lili.modules.wallet.entity.dos.MemberWallet entity, com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> updateWrapper) { return false; }
        @Override public boolean updateBatchById(java.util.Collection<cn.lili.modules.wallet.entity.dos.MemberWallet> entityList, int batchSize) { return false; }
        @Override public boolean saveOrUpdate(cn.lili.modules.wallet.entity.dos.MemberWallet entity) { return false; }
        @Override public cn.lili.modules.wallet.entity.dos.MemberWallet getById(java.io.Serializable id) { return null; }
        @Override public java.util.Collection<cn.lili.modules.wallet.entity.dos.MemberWallet> listByIds(java.util.Collection<? extends java.io.Serializable> idList) { return null; }
        @Override public java.util.Collection<cn.lili.modules.wallet.entity.dos.MemberWallet> listByMap(java.util.Map<String, Object> columnMap) { return null; }
        @Override public cn.lili.modules.wallet.entity.dos.MemberWallet getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper, boolean throwEx) { return null; }
        @Override public java.util.Map<String, Object> getMap(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return null; }
        @Override public <V> V getObj(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper, java.util.function.Function<? super Object, V> mapper) { return null; }
        @Override public long count(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return 0; }
        @Override public java.util.List<cn.lili.modules.wallet.entity.dos.MemberWallet> list(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return null; }
        @Override public com.baomidou.mybatisplus.core.metadata.IPage<cn.lili.modules.wallet.entity.dos.MemberWallet> page(com.baomidou.mybatisplus.core.metadata.IPage<cn.lili.modules.wallet.entity.dos.MemberWallet> page, com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return null; }
        @Override public java.util.List<java.util.Map<String, Object>> listMaps(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return null; }
        @Override public <V> java.util.List<V> listObjs(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper, java.util.function.Function<? super Object, V> mapper) { return null; }
        @Override public com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> pageMaps(com.baomidou.mybatisplus.core.metadata.IPage<cn.lili.modules.wallet.entity.dos.MemberWallet> page, com.baomidou.mybatisplus.core.conditions.Wrapper<cn.lili.modules.wallet.entity.dos.MemberWallet> queryWrapper) { return null; }
        @Override public cn.lili.modules.wallet.entity.dos.MemberWallet getBaseMapper() { return null; }
    }

    static class StubSearchPage<T> implements SearchPage<T> {
        private List<SearchHit<T>> content;
        private long totalElements;
        private int totalPages;

        public StubSearchPage(List<SearchHit<T>> content, long totalElements, int totalPages) {
            this.content = content;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        @Override public org.springframework.data.elasticsearch.core.SearchHits<T> getSearchHits() { return null; }
        @Override public int getTotalPages() { return totalPages; }
        @Override public long getTotalElements() { return totalElements; }
        @Override public <U> org.springframework.data.domain.Page<U> map(java.util.function.Function<? super SearchHit<T>, ? extends U> converter) { return null; }
        @Override public int getNumber() { return 0; }
        @Override public int getSize() { return 0; }
        @Override public int getNumberOfElements() { return 0; }
        @Override public java.util.List<SearchHit<T>> getContent() { return content; }
        @Override public boolean hasContent() { return content != null && !content.isEmpty(); }
        @Override public org.springframework.data.domain.Sort getSort() { return null; }
        @Override public boolean isFirst() { return false; }
        @Override public boolean isLast() { return false; }
        @Override public boolean hasNext() { return false; }
        @Override public boolean hasPrevious() { return false; }
        @Override public org.springframework.data.domain.Pageable nextPageable() { return null; }
        @Override public org.springframework.data.domain.Pageable previousPageable() { return null; }
        @Override public java.util.Iterator<SearchHit<T>> iterator() { return content.iterator(); }
    }
}
