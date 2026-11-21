package com.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

class LinkApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LinkRepository linkRepository;
    @Autowired MeterRegistry meterRegistry;

    private String createBody(String longUrl) {
        return "{\"longUrl\":\"" + longUrl + "\"}";
    }

    @Test
    void createThenRedirect() throws Exception {
        String longUrl = "https://example.com/some/very/long/path?q=1";

        String json = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(longUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.longUrl").value(longUrl))
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(json);
        String code = body.get("shortCode").asText();
        assertThat(body.get("shortUrl").asText()).endsWith("/" + code);

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }

    @Test
    void sameLongUrlTwiceGivesDifferentCodes() throws Exception {
        String a = createAndGetCode("https://example.com/dup");
        String b = createAndGetCode("https://example.com/dup");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mvc.perform(get("/zzzzzzzz")).andExpect(status().isNotFound());
    }

    @Test
    void expiredLinkReturns410() throws Exception {
        long id = linkRepository.nextId();
        String code = Base62.encode(id);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        linkRepository.save(new Link(id, code, "https://example.com/old", now.minusDays(2), now.minusDays(1)));

        mvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    @Test
    void invalidUrlsReturn400() throws Exception {
        for (String bad : new String[] {"not a url", "ftp://example.com/file", "javascript:alert(1)", "https://"}) {
            mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(createBody(bad)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongUrlReturns400() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(2100);
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(createBody(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pastExpiryReturns400() throws Exception {
        String body = "{\"longUrl\":\"https://example.com\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}";
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metricsCountCreatesAndRedirectOutcomes() throws Exception {
        double created = meterRegistry.counter("links.created").count();
        double hits = meterRegistry.counter("links.redirects", "outcome", "hit").count();
        double misses = meterRegistry.counter("links.redirects", "outcome", "not_found").count();

        String code = createAndGetCode("https://example.com/metrics");
        mvc.perform(get("/" + code)).andExpect(status().isFound());
        mvc.perform(get("/nope0000")).andExpect(status().isNotFound());

        assertThat(meterRegistry.counter("links.created").count()).isEqualTo(created + 1);
        assertThat(meterRegistry.counter("links.redirects", "outcome", "hit").count()).isEqualTo(hits + 1);
        assertThat(meterRegistry.counter("links.redirects", "outcome", "not_found").count()).isEqualTo(misses + 1);
    }

    private String createAndGetCode(String longUrl) throws Exception {
        String json = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(longUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("shortCode").asText();
    }
}
