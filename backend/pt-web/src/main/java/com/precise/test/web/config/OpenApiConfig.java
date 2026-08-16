package com.precise.test.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置（Swagger UI: /swagger-ui.html）
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI preciseTestOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("精准测试平台 API")
                        .description("自动识别被测项目接口 → 自动生成接口用例 → 建立用例与代码的关联关系")
                        .version("0.1.0"));
    }
}
