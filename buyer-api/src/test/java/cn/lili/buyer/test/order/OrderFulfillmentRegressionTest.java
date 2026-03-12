package cn.lili.buyer.test.order;

import cn.lili.common.security.AuthUser;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.modules.order.cart.entity.dto.TradeDTO;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import cn.lili.modules.order.cart.entity.vo.CartVO;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.dos.Trade;
import cn.lili.modules.order.order.entity.enums.PayStatusEnum;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.order.order.service.TradeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Order Fulfillment Regression Test
 * Deepens Engineering Quality by verifying core business flows.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${LILI_DB_URL:jdbc:mysql://127.0.0.1:3306/lilishop?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=${LILI_DB_USER:root}",
        "spring.datasource.password=${LILI_DB_PASSWORD:lilishop}",
        "spring.data.redis.password=${LILI_REDIS_PASSWORD:lilishop}"
}, classes = {cn.lili.BuyerApiApplication.class, OrderFulfillmentRegressionTest.TestOverrides.class})
@Transactional
public class OrderFulfillmentRegressionTest {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setupMember() {
        // AuthUser(String username, String id, String nickName, String face, UserEnums role)
        new AuthUser("test-member", "1", "test-nick", "test-face", UserEnums.MEMBER);
        // Note: Lilishop UserContext relies on RequestHeader. 
        // In this Service-level test, we bypass Controller layer, so if service methods 
        // need current user, we'd need to mock RequestContextHolder.
    }

    @Test
    void testOrderToPaymentFlow() {
        // 1. Prepare TradeDTO
        TradeDTO tradeDTO = new TradeDTO(CartTypeEnum.CART);
        tradeDTO.setMemberId("1");
        tradeDTO.setMemberName("test-member");
        
        List<CartVO> cartList = new ArrayList<>();
        // Creating a minimal CartVO might be complex, let's see if we can use a simpler route
        // or just verify that if we have a trade, payment works.
        
        // Let's create a Trade manually if createTrade is too heavy for unit test environment
        Trade trade = new Trade();
        trade.setSn("TR" + System.currentTimeMillis());
        trade.setMemberId("1");
        trade.setMemberName("test-member");
        trade.setPayStatus(PayStatusEnum.UNPAID.name());
        trade.setFlowPrice(100.0);
        tradeService.save(trade);

        // Create an associated Order
        Order order = new Order();
        order.setSn("O" + System.currentTimeMillis());
        order.setTradeSn(trade.getSn());
        order.setMemberId("1");
        order.setMemberName("test-member");
        order.setPayStatus(PayStatusEnum.UNPAID.name());
        order.setFlowPrice(100.0);
        orderService.save(order);

        // 2. Execute Payment
        orderService.payOrder(order.getSn(), "WECHAT", "PAY123456");

        // 3. Verify Result
        Order updatedOrder = orderService.getBySn(order.getSn());
        Assertions.assertEquals(PayStatusEnum.PAID.name(), updatedOrder.getPayStatus(), "Order status should be PAID");
        
        // Verify payment total
        Double total = orderService.getPaymentTotal(order.getSn());
        Assertions.assertEquals(100.0, total);
        
        System.out.println("✅ Order Fulfillment Flow Verified: " + order.getSn());
    }

    @TestConfiguration
    static class TestOverrides {
        @Bean(name = "redisson")
        @Primary
        RedissonClient redissonStub() {
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class<?>[]{RedissonClient.class},
                    (proxy, method, args) -> {
                        Class<?> returnType = method.getReturnType();
                        if (returnType.equals(boolean.class)) return false;
                        if (returnType.equals(byte.class)) return (byte) 0;
                        if (returnType.equals(short.class)) return (short) 0;
                        if (returnType.equals(int.class)) return 0;
                        if (returnType.equals(long.class)) return 0L;
                        if (returnType.equals(float.class)) return 0f;
                        if (returnType.equals(double.class)) return 0d;
                        if (returnType.equals(char.class)) return '\0';
                        return null;
                    }
            );
        }
    }
}
