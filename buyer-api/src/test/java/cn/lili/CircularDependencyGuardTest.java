package cn.lili;

import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.promotion.service.MemberCouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = { "spring.main.allow-circular-references=false" }, classes = BuyerApiApplication.class)
public class CircularDependencyGuardTest {

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Autowired(required = false)
    private MemberCouponService memberCouponService;

    @Test
    public void contextLoads() {
        assertNotNull(goodsSkuService, "GoodsSkuService should gracefully load without circular dependencies.");
    }
}
