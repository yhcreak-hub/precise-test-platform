package com.precise.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 精准测试平台启动类
 *
 * <p>扫描 {@code com.precise.test} 包下所有模块：
 * pt-common / pt-auth / pt-repo / pt-analyze / pt-casegen / pt-mapping / pt-task / pt-web。</p>
 */
@SpringBootApplication(scanBasePackages = "com.precise.test")
@EnableAsync
public class PreciseTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PreciseTestApplication.class, args);
    }
}
