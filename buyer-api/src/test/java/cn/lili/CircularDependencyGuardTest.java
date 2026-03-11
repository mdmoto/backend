package cn.lili;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Proxy;

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
}, classes = {BuyerApiApplication.class, CircularDependencyGuardTest.TestOverrides.class})
public class CircularDependencyGuardTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void contextLoads() {
        assertNotNull(redissonClient);
    }

    @TestConfiguration
    static class TestOverrides {
        /**
         * Provide a lightweight RedissonClient stub so the context can boot even if Redis is not
         * running locally. We avoid Mockito here because some JDK distributions restrict agent
         * attachment, which can break @MockBean initialization.
         */
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
