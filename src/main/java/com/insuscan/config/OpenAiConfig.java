package com.insuscan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.util.OpenAiJsonParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfig {

    @Bean
    public OpenAiJsonParser openAiJsonParser(ObjectMapper objectMapper) {
        return new OpenAiJsonParser(objectMapper);
    }
}