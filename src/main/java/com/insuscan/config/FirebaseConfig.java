package com.insuscan.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.config.path:firebase-service-account.json}")
    private String firebaseConfigPath;

    @Value("${firebase.project.id:insuscan-project}")
    private String projectId;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = buildFirebaseOptions();
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully for project: {}", projectId);
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase", e);
            throw new RuntimeException("Could not initialize Firebase", e);
        }
    }

    private FirebaseOptions buildFirebaseOptions() throws IOException {
        GoogleCredentials credentials = loadCredentials();
        
        return FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();
    }

    private GoogleCredentials loadCredentials() throws IOException {
        // Try loading from classpath first
        InputStream serviceAccount = getClass().getClassLoader()
                .getResourceAsStream(firebaseConfigPath);
        
        if (serviceAccount == null) {
            // Try loading from file system
            serviceAccount = new FileInputStream(firebaseConfigPath);
        }
        
        if (serviceAccount == null) {
            // Use default credentials (for cloud environments)
            log.info("No service account file found, using default credentials");
            return GoogleCredentials.getApplicationDefault();
        }
        
        return GoogleCredentials.fromStream(serviceAccount);
    }

    @Bean
    public Firestore firestore() {
        return FirestoreClient.getFirestore();
    }
}
