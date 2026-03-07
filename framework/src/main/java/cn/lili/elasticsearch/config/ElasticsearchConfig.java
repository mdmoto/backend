package cn.lili.elasticsearch.config;

import cn.hutool.core.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * elasticsearch 配置
 *
 * @author paulG
 * @since 2020/10/13
 **/
@Slf4j
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Override
    public ClientConfiguration clientConfiguration() {
        String username = elasticsearchProperties.getAccount().getUsername();
        String password = elasticsearchProperties.getAccount().getPassword();

        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = ClientConfiguration.builder()
                .connectedTo(elasticsearchProperties.getClusterNodes().toArray(new String[0]));

        if (username != null && password != null) {
            builder.withBasicAuth(username, password);
        }

        return builder.build();
    }
}
