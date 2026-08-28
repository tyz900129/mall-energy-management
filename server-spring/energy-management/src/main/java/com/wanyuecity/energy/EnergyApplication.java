package com.wanyuecity.energy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry. Extends SpringBootServletInitializer so the war can be
 * deployed into an external Tomcat (Bitnami 9.0.98 on port 8866 in this env).
 */
@SpringBootApplication
@MapperScan("com.wanyuecity.energy.**.mapper")
@EnableCaching
@EnableScheduling
public class EnergyApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(EnergyApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(EnergyApplication.class, args);
    }
}

