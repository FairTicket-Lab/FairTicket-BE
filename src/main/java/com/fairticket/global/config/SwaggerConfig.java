package com.fairticket.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI fairTicketOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FairTicket API")
                        .description("대용량 트래픽 처리 티켓팅 시스템 API 문서")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FairTicket Team")
                                .email("support@fairticket.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버")
                ));
    }
}
