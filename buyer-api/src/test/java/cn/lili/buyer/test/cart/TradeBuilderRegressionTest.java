package cn.lili.buyer.test.cart;

import cn.lili.modules.order.cart.entity.dto.TradeDTO;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import cn.lili.modules.order.cart.render.CartRenderStep;
import cn.lili.modules.order.cart.render.TradeBuilder;
import cn.lili.modules.order.cart.service.CartPersistenceService;
import cn.lili.modules.order.order.service.TradeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TradeBuilder Regression Test
 * Ensures that the refactored TradeBuilder (breaking circular dependency)
 * still correctly constructs TradeDTO.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TradeBuilderRegressionTest.TestConfig.class)
class TradeBuilderRegressionTest {

    @Autowired
    private TradeBuilder tradeBuilder;

    @Autowired
    private CartPersistenceService cartPersistenceService;

    @Test
    void testBuildCart() {
        // Setup mock data
        TradeDTO mockDTO = new TradeDTO(CartTypeEnum.CART);
        mockDTO.setMemberId("test-member-id");
        mockDTO.setMemberName("test-member");

        when(cartPersistenceService.readDTO(any())).thenReturn(mockDTO);

        // Execute build
        TradeDTO result = tradeBuilder.buildCart(CartTypeEnum.CART);

        // Verify key fields
        Assertions.assertNotNull(result);
        Assertions.assertEquals("test-member-id", result.getMemberId());
        Assertions.assertEquals(CartTypeEnum.CART, result.getCartTypeEnum());
        // Verify that coupons were reset for CART type (logic in buildCart)
        Assertions.assertNull(result.getStoreCoupons());
        Assertions.assertNull(result.getPlatformCoupon());
    }

    @Test
    void testBuildChecked() {
        // Setup mock data for BUY_NOW
        TradeDTO mockDTO = new TradeDTO(CartTypeEnum.BUY_NOW);
        mockDTO.setMemberId("test-member-id");

        when(cartPersistenceService.readDTO(CartTypeEnum.BUY_NOW)).thenReturn(mockDTO);

        // Execute build
        TradeDTO result = tradeBuilder.buildChecked(CartTypeEnum.BUY_NOW);

        // Verify
        Assertions.assertNotNull(result);
        Assertions.assertEquals(CartTypeEnum.BUY_NOW, result.getCartTypeEnum());
    }

    @Configuration
    static class TestConfig {
        @Bean
        TradeBuilder tradeBuilder() {
            return new TradeBuilder();
        }

        @Bean
        CartPersistenceService cartPersistenceService() {
            return mock(CartPersistenceService.class);
        }

        @Bean
        TradeService tradeService() {
            return mock(TradeService.class);
        }

        @Bean
        List<CartRenderStep> cartRenderSteps() {
            return Collections.emptyList();
        }
    }
}
