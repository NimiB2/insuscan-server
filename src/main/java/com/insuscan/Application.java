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
	
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Bean
    public CommandLineRunner checkKey() {
        return args -> {
            System.out.println("=======================================");
            if (openAiKey != null && !openAiKey.isEmpty()) {
                // מדפיס רק את ההתחלה כדי שתראה שזה המפתח הנכון בלי לחשוף אותו
                System.out.println("✅ OpenAI Key loaded successfully!");
                System.out.println("Starts with: " + openAiKey.substring(0, 5) + "...");
            } else {
                System.out.println("❌ ERROR: OpenAI Key is missing!");
            }
            System.out.println("=======================================");
        };
    }
}
