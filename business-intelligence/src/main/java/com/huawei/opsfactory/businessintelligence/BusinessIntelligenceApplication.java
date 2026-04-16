package com.huawei.opsfactory.businessintelligence;

import com.huawei.opsfactory.businessintelligence.config.BusinessIntelligenceRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BusinessIntelligenceRuntimeProperties.class)
public class BusinessIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessIntelligenceApplication.class, args);
    }
}

