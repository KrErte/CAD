package ee.krerte.cad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableAsync
public class CadApplication {

    public static void main(String[] args) {
        SpringApplication.run(CadApplication.class, args);
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    // BUG-FIX: CORS piirang — varem oli allowedOrigins("*"), mis lubab iga domeeni.
    // Tootmises peab olema ainult oma domeen, arenduses localhost.
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public WebMvcConfigurer webConfig(@Autowired MetricsController metrics) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // BUG-FIX: piiratud lubatud päritolud — ainult tehisaicad.ee + localhost (dev)
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "https://tehisaicad.ee",
                                "https://www.tehisaicad.ee",
                                frontendUrl, // arenduses http://localhost:4200
                                "http://localhost:4200")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(metrics).addPathPatterns("/api/**");
            }
        };
    }
}
