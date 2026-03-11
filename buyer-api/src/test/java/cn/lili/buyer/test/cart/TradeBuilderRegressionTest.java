package cn.lili.buyer.test.cart;

import cn.lili.modules.order.cart.entity.dto.TradeDTO;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import cn.lili.modules.order.cart.render.CartRenderStep;
import cn.lili.modules.order.cart.render.TradeBuilder;
import cn.lili.modules.order.cart.service.CartPersistenceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TradeBuilder Regression Test
 * Ensures that the refactored TradeBuilder (breaking circular dependency)
 * still correctly constructs TradeDTO.
 */
class TradeBuilderRegressionTest {

    @Test
    void testBuildCart() {
        TradeBuilder tradeBuilder = new TradeBuilder();
        AtomicReference<TradeDTO> next = new AtomicReference<>();
        setField(tradeBuilder, "cartRenderSteps", Collections.<CartRenderStep>emptyList());
        setField(tradeBuilder, "cartPersistenceService", new TestCartPersistenceService(next));

        // Setup mock data
        TradeDTO mockDTO = new TradeDTO(CartTypeEnum.CART);
        mockDTO.setMemberId("test-member-id");
        mockDTO.setMemberName("test-member");

        next.set(mockDTO);

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
        TradeBuilder tradeBuilder = new TradeBuilder();
        AtomicReference<TradeDTO> next = new AtomicReference<>();
        setField(tradeBuilder, "cartRenderSteps", Collections.<CartRenderStep>emptyList());
        setField(tradeBuilder, "cartPersistenceService", new TestCartPersistenceService(next));

        // Setup mock data for BUY_NOW
        TradeDTO mockDTO = new TradeDTO(CartTypeEnum.BUY_NOW);
        mockDTO.setMemberId("test-member-id");

        next.set(mockDTO);

        // Execute build
        TradeDTO result = tradeBuilder.buildChecked(CartTypeEnum.BUY_NOW);

        // Verify
        Assertions.assertNotNull(result);
        Assertions.assertEquals(CartTypeEnum.BUY_NOW, result.getCartTypeEnum());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set field: " + fieldName, e);
        }
    }

    static class TestCartPersistenceService implements CartPersistenceService {
        private final AtomicReference<TradeDTO> next;

        TestCartPersistenceService(AtomicReference<TradeDTO> next) {
            this.next = next;
        }

        @Override
        public TradeDTO readDTO(CartTypeEnum checkedWay) {
            return next.get();
        }

        @Override
        public void resetTradeDTO(TradeDTO tradeDTO) {
            // No-op for regression tests.
        }

        @Override
        public void clean() {
            // No-op for regression tests.
        }
    }
}
