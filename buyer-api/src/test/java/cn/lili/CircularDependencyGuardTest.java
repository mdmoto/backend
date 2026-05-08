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
        "spring.datasource.url=${LILI_DB_URL:jdbc:mysql://127.0.0.1:3306/lilishop?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=${LILI_DB_USER:root}",
        "spring.datasource.password=${LILI_DB_PASSWORD:lilishop}",
        "spring.datasource.hikari.initializationFailTimeout=0",

        // Help Lettuce find the correct Redis password when running in CI/Azure context
        "spring.data.redis.password=${LILI_REDIS_PASSWORD:lilishop}",

        // Avoid environment-coupled failures: this guard is about bean graph cycles.
        "spring.data.elasticsearch.repositories.enabled=false"
}, classes = {BuyerApiApplication.class, CircularDependencyGuardTest.TestOverrides.class})
@org.springframework.test.context.TestExecutionListeners(listeners = {
        org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        org.springframework.test.context.support.DirtiesContextTestExecutionListener.class
}, mergeMode = org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
public class CircularDependencyGuardTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private cn.lili.cache.Cache cache;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    public void contextLoads() {
        assertNotNull(redissonClient);
        assertNotNull(cache);
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

        @Bean
        @Primary
        cn.lili.cache.Cache cacheStub() {
            return (cn.lili.cache.Cache) Proxy.newProxyInstance(
                    cn.lili.cache.Cache.class.getClassLoader(),
                    new Class<?>[]{cn.lili.cache.Cache.class},
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
                        if (returnType.equals(Long.class)) return 1L; // For cache.incr()
                        return null;
                    }
            );
        }

        @Bean
        @Primary
        org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplateStub() {
            org.springframework.data.redis.core.StringRedisTemplate template = new org.springframework.data.redis.core.StringRedisTemplate();
            org.springframework.data.redis.connection.RedisConnectionFactory factory = (org.springframework.data.redis.connection.RedisConnectionFactory) Proxy.newProxyInstance(
                    org.springframework.data.redis.connection.RedisConnectionFactory.class.getClassLoader(),
                    new Class<?>[]{org.springframework.data.redis.connection.RedisConnectionFactory.class},
                    (proxy, method, args) -> null
            );
            template.setConnectionFactory(factory);
            return template;
        }
    }
}
