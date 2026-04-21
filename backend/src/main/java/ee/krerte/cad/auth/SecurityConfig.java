package ee.krerte.cad.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final String frontendUrl;

    public SecurityConfig(@Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http,
                                     JwtAuthFilter jwtFilter,
                                     OAuth2SuccessHandler oauthSuccess,
                                     OAuth2UserService<OAuth2UserRequest, OAuth2User> oauthUserService) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfig()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .headers(h -> h
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000))
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> {})
                .referrerPolicy(r -> r.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(p -> p.policy(
                    "camera=(), microphone=(), geolocation=(), payment=(self)"))
                .xssProtection(x -> x.headerValue(
                    XXssProtectionHeaderWriter.HeaderValue.DISABLED))
                .contentSecurityPolicy(csp -> csp.policyDirectives(buildCsp()))
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                }))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/api/stripe/webhook",
                                 "/api/templates", "/api/health",
                                 "/api/gallery",
                                 "/api/gallery/*/stl",
                                 "/api/orders/quote",
                                 "/api/pricing/**",
                                 "/actuator/health/**",
                                 "/actuator/info",
                                 "/oauth2/**", "/login/**",
                                 "/ws/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/me", "/api/spec", "/api/generate", "/api/generate/**", "/api/meshy",
                                 "/api/metrics", "/api/preview", "/api/review",
                                 "/api/billing/**",
                                 "/api/stripe/checkout", "/api/stripe/subscription",
                                 "/api/stripe/portal",
                                 "/api/designs/**", "/api/admin/**",
                                 "/api/gallery/**",
                                 "/api/orders/**",
                                 "/api/evolve/**", "/api/freeform/**").authenticated()
                .anyRequest().permitAll())
            .oauth2Login(o -> o
                .userInfoEndpoint(u -> u.userService(oauthUserService))
                .successHandler(oauthSuccess))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfig() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
            frontendUrl,
            "http://localhost:4200",
            "https://cad.krerte.ee"
        ));
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Idempotency-Key", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Remaining"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        var src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    private static String buildCsp() {
        return String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-eval' https://js.stripe.com https://checkout.stripe.com",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com data:",
            "img-src 'self' data: blob: https:",
            "connect-src 'self' ws: wss: https://api.stripe.com https://checkout.stripe.com",
            "frame-src https://js.stripe.com https://checkout.stripe.com https://hooks.stripe.com",
            "worker-src 'self' blob:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self' https://checkout.stripe.com https://accounts.google.com",
            "frame-ancestors 'none'"
        );
    }
}
