package com.urlshortener.shortener;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.shortener.*}.
 *
 * @param baseUrl      public base URL used to build {@code shortUrl} in responses
 * @param maxUrlLength maximum accepted length of a submitted long URL
 */
@ConfigurationProperties("app.shortener")
public record ShortenerProperties(String baseUrl, int maxUrlLength) {
}
