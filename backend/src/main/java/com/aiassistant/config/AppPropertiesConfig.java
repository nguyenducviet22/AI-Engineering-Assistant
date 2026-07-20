package com.aiassistant.config;

import com.aiassistant.repository.service.ZipValidationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ZipValidationProperties.class)
public class AppPropertiesConfig {
}
