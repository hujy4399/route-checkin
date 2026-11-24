package com.example.route_checkin;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class AdminBootstrap {
    @Bean
    ApplicationRunner initialAdmin(ProjectService service, Environment env) {
        return args -> service.bootstrapAdmin(
            env.getProperty("APP_ADMIN_USERNAME"), env.getProperty("APP_ADMIN_PASSWORD"));
    }
}
