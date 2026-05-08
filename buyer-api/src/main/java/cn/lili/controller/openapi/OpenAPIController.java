package cn.lili.controller.openapi;

import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.openapi.aop.OpenApiRateLimit;
import cn.lili.modules.openapi.service.AIService;
import cn.lili.modules.search.entity.dos.EsGoodsIndex;
import cn.lili.modules.search.entity.dto.EsGoodsSearchDTO;
import cn.lili.modules.search.service.EsGoodsSearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import cn.lili.modules.order.cart.service.CartService;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import cn.lili.modules.order.cart.entity.vo.TradeParams;
import cn.lili.modules.order.order.entity.dos.Trade;
import cn.lili.modules.wallet.service.MemberWalletService;
import cn.lili.modules.wallet.entity.dto.MemberWalletUpdateDTO;
import cn.lili.common.exception.ServiceException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Api(tags = "OpenAPI, 第三方开放接口")
@RequestMapping("/buyer/openapi")
public class OpenAPIController {

    @Autowired
    private EsGoodsSearchService esGoodsSearchService;

    @Autowired
    private AIService aiService;

    @Autowired
    private CartService cartService;

    @Autowired
    private MemberWalletService memberWalletService;


    @OpenApiRateLimit(name = "ProductSearch", minLimit = 1000, dayLimit = 50000)
    @GetMapping("/product/search")
    @ApiOperation(value = "商品搜索接口 (携带 LLM 摘要)")
    public ResultMessage<Map<String, Object>> searchProducts(EsGoodsSearchDTO searchDTO, PageVO pageVo) {
        // Perform the standard Elasticsearch product search
        SearchPage<EsGoodsIndex> searchPage = esGoodsSearchService.searchGoods(searchDTO, pageVo);

        // Enhance the response with AI Summaries
        List<Map<String, Object>> enhancedContent = searchPage.getContent().stream().map(hit -> {
            EsGoodsIndex goods = hit.getContent();
            Map<String, Object> map = new HashMap<>();
            map.put("goodsId", goods.getGoodsId());
            map.put("skuId", goods.getId());
            map.put("goodsName", goods.getGoodsName());
            map.put("price", goods.getPrice());
            map.put("thumbnail", goods.getThumbnail());

            // Add the ai_summary field
            String summary = aiService.generateGoodsSummary(goods.getGoodsName(), goods.getPrice());
            map.put("ai_summary", summary);

            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", enhancedContent);
        result.put("totalElements", searchPage.getTotalElements());
        result.put("totalPages", searchPage.getTotalPages());

        return ResultUtil.data(result);
    }

    @OpenApiRateLimit(name = "OrderCreate", minLimit = 5, dayLimit = 20)
    @PostMapping("/order/create")
    @ApiOperation(value = "创建订单接口")
    @Transactional(rollbackFor = Exception.class)
    public ResultMessage<String> createOrder(@RequestBody Map<String, Object> orderPayload, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String memberId = (String) request.getAttribute("OpenApiMemberId");
        if (memberId == null) {
            throw new ServiceException("Unauthorized API Request");
        }

        String skuId = (String) orderPayload.get("skuId");
        Integer num = orderPayload.get("num") != null ? Integer.valueOf(orderPayload.get("num").toString()) : 1;

        if (skuId == null) {
            throw new ServiceException("SKU ID is required");
        }

        // 1. Add to Cart (BUY_NOW mode, override existing)
        cartService.add(skuId, num, CartTypeEnum.BUY_NOW.name(), true);

        // 2. Create Trade
        TradeParams params = new TradeParams();
        params.setWay(CartTypeEnum.BUY_NOW.name());
        Trade trade = cartService.createTrade(params);

        // 3. Deduct Wallet Balance (Strict Check)
        cn.lili.modules.wallet.entity.vo.MemberWalletVO wallet = memberWalletService.getMemberWallet(memberId);
        if (wallet == null || wallet.getMemberWallet() == null || wallet.getMemberWallet() < trade.getFlowPrice()) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            response.setStatus(403);
            return ResultUtil.error(403, "Forbidden: Insufficient Prepaid Deposit (Member Wallet)");
        }

        MemberWalletUpdateDTO walletUpdate = new MemberWalletUpdateDTO(
            trade.getFlowPrice(),
            memberId,
            "OpenAPI Order Deduction - SN: " + trade.getSn(),
            cn.lili.modules.wallet.entity.enums.DepositServiceTypeEnum.WALLET_PAY.name()
        );
        
        Boolean success = memberWalletService.reduce(walletUpdate);
        if (success == null || !success) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            response.setStatus(403);
            return ResultUtil.error(403, "Forbidden: Deduction Failed");
        }

        return ResultUtil.data(trade.getSn());
    }
}
