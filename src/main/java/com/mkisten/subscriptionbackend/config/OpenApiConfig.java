package com.mkisten.subscriptionbackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Subscription Backend API")
                        .description("""
                            API для управления подписками и аутентификацией через Telegram
                            
                            ## Аутентификация
                            1. Получите токен через `/api/auth/token` с Telegram ID
                            2. Используйте токен в заголовке: `Authorization: Bearer <token>`
                            
                            ## Доступные роли
                            - **Пользователь** - базовый доступ к своему профилю и подписке
                            - **Администратор** - полный доступ ко всем функциям
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Support Team")
                                .email("support@mkisten.com")
                                .url("https://mkisten.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Development Server"),
                        new Server()
                                .url("https://api.yourdomain.com")
                                .description("Production Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("JWT", new SecurityScheme()
                                .name("JWT")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Введите JWT токен. Получите токен через /api/auth/token")));
    }
}