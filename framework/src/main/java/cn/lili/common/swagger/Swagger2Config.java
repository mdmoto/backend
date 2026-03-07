package cn.lili.common.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI 3 Configuration (P3 Upgrade)
 * Replaces legacy Springfox/Swagger 2
 */
@Slf4j
@Configuration
public class Swagger2Config {

    @Value("${swagger.title:Maollar API}")
    private String title;

    @Value("${swagger.description:Maollar Open API}")
    private String description;

    @Value("${swagger.version:4.3}")
    private String version;

    @Value("${swagger.contact.name:Chopper}")
    private String name;

    @Value("${swagger.contact.url:}")
    private String url;

    @Value("${swagger.contact.email:}")
    private String email;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact().name(name).url(url).email(email)))
                .addSecurityItem(new SecurityRequirement().addList("Authorization"))
                .components(new Components()
                        .addSecuritySchemes("Authorization",
                                new SecurityScheme()
                                        .name("accessToken")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Bearer Token")));
    }

    @Bean
    public GroupedOpenApi goodsApi() {
        return GroupedOpenApi.builder()
                .group("商品")
                .pathsToMatch("/buyer/goods/**", "/seller/goods/**", "/manager/goods/**")
                .build();
    }

    @Bean
    public GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder()
                .group("订单")
                .pathsToMatch("/buyer/order/**", "/seller/order/**", "/manager/order/**")
                .build();
    }

    @Bean
    public GroupedOpenApi memberApi() {
        return GroupedOpenApi.builder()
                .group("会员")
                .pathsToMatch("/buyer/member/**", "/seller/member/**", "/manager/member/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tradeApi() {
        return GroupedOpenApi.builder()
                .group("交易")
                .pathsToMatch("/buyer/trade/**", "/seller/trade/**", "/manager/trade/**")
                .build();
    }

    @Bean
    public GroupedOpenApi passportApi() {
        return GroupedOpenApi.builder()
                .group("登录")
                .pathsToMatch("/buyer/passport/**", "/seller/passport/**", "/manager/passport/**")
                .build();
    }

    @Bean
    public GroupedOpenApi managerApi() {
        return GroupedOpenApi.builder()
                .group("管理端")
                .pathsToMatch("/manager/**")
                .build();
    }

    @Bean
    public GroupedOpenApi buyerApi() {
        return GroupedOpenApi.builder()
                .group("买家端")
                .pathsToMatch("/buyer/**")
                .build();
    }

    @Bean
    public GroupedOpenApi sellerApi() {
        return GroupedOpenApi.builder()
                .group("卖家端")
                .pathsToMatch("/seller/**")
                .build();
    }
}
