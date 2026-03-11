package cn.lili;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=false",
        // Allow tests to override infra beans (e.g. Redis/Redisson) so this guard
        // focuses on dependency graph integrity rather than local middleware.
        "spring.main.allow-bean-definition-overriding=true",

        // Provide a JDBC baseline so DataSource auto-config can initialize.
        // Avoid failing the whole context if MySQL isn't running locally.
        "spring.datasource.url=${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/lilishop?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=${MYSQL_USER:root}",
        "spring.datasource.password=${MYSQL_PASSWORD:lilishop}",
        "spring.datasource.hikari.initializationFailTimeout=0",

        // Avoid environment-coupled failures: this guard is about bean graph cycles.
        "spring.data.elasticsearch.repositories.enabled=false"
}, classes = BuyerApiApplication.class)
public class CircularDependencyGuardTest {

    // Prevent context boot failures when Redis isn't available locally.
    @MockBean(name = "redisson")
    private RedissonClient redissonClient;

    @Test
    public void contextLoads() {
        assertNotNull(redissonClient);
    }
}
