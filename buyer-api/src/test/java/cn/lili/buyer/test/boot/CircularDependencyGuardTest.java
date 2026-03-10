package cn.lili.buyer.test.boot;

import cn.lili.modules.promotion.service.PromotionGoodsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.allow-circular-references=false"
        }
)
@Tag("guard")
class CircularDependencyGuardTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsWithCircularReferencesDisabled() {
        boolean allowCircular = Boolean.parseBoolean(
                environment.getProperty("spring.main.allow-circular-references", "false")
        );
        assertFalse(allowCircular, "allow-circular-references must stay disabled for this guard test");

        // Force bean resolution to ensure promotion graph can be instantiated.
        assertNotNull(applicationContext.getBean(PromotionGoodsService.class));
    }
}

