package com.urlshortener.shortener;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShortenerProperties.class)
class ShortenerConfig {
}
