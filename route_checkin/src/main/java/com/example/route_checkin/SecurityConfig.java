package com.example.route_checkin;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/styles.css", "/app.js",
                    "/favicon.svg", "/hello", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/routes", "/api/routes/*",
                    "/api/products", "/api/map-points", "/api/keywords", "/api/rankings/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return username -> {
            List<java.util.Map<String, Object>> users = jdbc.queryForList(
                "SELECT user_id, username, password_hash, account_status FROM app_user WHERE username = ?",
                username);
            if (users.isEmpty()) throw new UsernameNotFoundException(username);
            var row = users.get(0);
            long userId = ((Number) row.get("user_id")).longValue();
            List<String> roles = jdbc.queryForList(
                "SELECT r.role_code FROM user_role ur JOIN app_role r ON r.role_id = ur.role_id WHERE ur.user_id = ?",
                String.class, userId);
            return User.withUsername(username)
                .password((String) row.get("password_hash"))
                .authorities(roles.stream().map(role -> "ROLE_" + role).toArray(String[]::new))
                .disabled(!"ACTIVE".equals(row.get("account_status")))
                .build();
        };
    }
}
