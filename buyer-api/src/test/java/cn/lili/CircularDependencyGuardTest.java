package cn.lili;

import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.promotion.service.MemberCouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=false",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/lilishop?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=lilishop"
}, classes = BuyerApiApplication.class)
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
