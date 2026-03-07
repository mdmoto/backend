package cn.lili.mybatis.heath;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthIndicatorProperties;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库检验工具
 *
 * @author Chopper
 * @version v4.0
 * @since 2020/12/24 19:31
 */

@Configuration
public class DataSourceHealthConfig {

    @Value("${spring.datasource.dbcp2.validation-query:select 1}")
    private String defaultQuery;

    /**
     * Spring Boot 2.7.x uses DataSourceHealthContributorAutoConfiguration to create the "db" health contributor.
     * We provide our own bean to ensure a stable validation query across different pool implementations.
     */
    @Bean
    public HealthContributor dbHealthContributor(Map<String, DataSource> dataSources,
                                                 DataSourceHealthIndicatorProperties properties) {
        // Keep ordering stable for predictable output.
        Map<String, HealthContributor> contributors = new LinkedHashMap<>();
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            DataSourceHealthIndicator indicator = new DataSourceHealthIndicator(entry.getValue());
            indicator.setQuery(defaultQuery);
            contributors.put(entry.getKey(), indicator);
        }
        return CompositeHealthContributor.fromMap(contributors);
    }
}
