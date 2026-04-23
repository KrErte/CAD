package ee.krerte.cad.auth;

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

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain chain(HttpSecurity http,
                                     JwtAuthFilter jwtFilter,
                                     OAuth2SuccessHandler oauthSuccess,
                                     OAuth2UserService<OAuth2UserRequest, OAuth2User> oauthUserService) throws Exception {
        http
            .cors(c -> {})
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/api/stripe/webhook",
                                 "/api/templates", "/api/health",
                                 "/api/gallery",
                                 "/api/gallery/*/stl",
                                 "/api/orders/quote",
                                 "/api/pricing/**",
                                 "/oauth2/**", "/login/**",
                                 "/ws/**").permitAll()
                .requestMatchers("/api/me", "/api/spec", "/api/invent", "/api/generate", "/api/generate/**", "/api/meshy",
                                 "/api/metrics", "/api/preview", "/api/review",
                                 "/api/billing/**",
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
}
