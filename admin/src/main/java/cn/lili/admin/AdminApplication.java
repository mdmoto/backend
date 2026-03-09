package cn.lili.admin;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.util.UUID;

/**
 * Admin
 *
 * @author Chopper
 * @since 2020/11/16 10:03 下午
 */
@SpringBootApplication
@EnableAdminServer
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

    @Configuration
    public static class SecuritySecureConfig {

        private final AdminServerProperties adminServer;

        public SecuritySecureConfig(AdminServerProperties adminServer) {
            this.adminServer = adminServer;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
            successHandler.setTargetUrlParameter("redirectTo");
            successHandler.setDefaultTargetUrl(this.adminServer.path("/"));

            return http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(this.adminServer.path("/assets/**")).permitAll()
                            .requestMatchers(this.adminServer.path("/login")).permitAll()
                            .requestMatchers("/actuator/**").permitAll()
                            .requestMatchers("/instances**").permitAll()
                            .anyRequest().authenticated())
                    .formLogin(form -> form.loginPage(this.adminServer.path("/login")).successHandler(successHandler))
                    .logout(logout -> logout.logoutUrl(this.adminServer.path("/logout")))
                    .httpBasic(Customizer.withDefaults())
                    .csrf(csrf -> csrf.disable())
                    .rememberMe(remember -> remember.key(UUID.randomUUID().toString()).tokenValiditySeconds(1209600))
                    .build();
        }

    }
}
