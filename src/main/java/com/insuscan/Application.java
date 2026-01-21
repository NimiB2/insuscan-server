package com.insuscan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;

@SpringBootApplication
public class Application {
	@Value("${openai.api.key}")
    private String openAiKey;
	
	@Value("${insuscan.usda.api.key}")
	private String usdaApiKey;

	
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Bean
    public CommandLineRunner checkKey() {
        return args -> {
            System.out.println("=======================================");

            // OpenAI
            if (openAiKey != null && !openAiKey.isEmpty()) {
                System.out.println("✅ OpenAI Key loaded successfully!");
                System.out.println("Starts with: " + openAiKey.substring(0, Math.min(5, openAiKey.length())) + "...");
            } else {
                System.out.println("❌ ERROR: OpenAI Key is missing!");
            }

            // USDA
            if (usdaApiKey != null && !usdaApiKey.isEmpty()) {
                System.out.println("✅ USDA Key loaded successfully!");
                System.out.println("Starts with: " + usdaApiKey.substring(0, Math.min(5, usdaApiKey.length())) + "...");
            } else {
                System.out.println("❌ ERROR: USDA Key is missing!");
            }

            System.out.println("=======================================");
        };
    }

}
