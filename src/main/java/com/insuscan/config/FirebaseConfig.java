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
    private static volatile FirebaseApp firebaseApp;

    @Value("${firebase.config.path:firebase-service-account.json}")
    private String firebaseConfigPath;

    @Value("${firebase.project.id:insuscan-project}")
    private String projectId;

    @PostConstruct
    public void initialize() {
        synchronized (FirebaseConfig.class) {
            try {
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseOptions options = buildFirebaseOptions();
                    firebaseApp = FirebaseApp.initializeApp(options);
                    log.info("Firebase initialized successfully for project: {}", projectId);
                } else {
                    firebaseApp = FirebaseApp.getInstance();
                    log.info("Firebase app already initialized, using existing instance");
                }
            } catch (Exception e) {
                log.error("Failed to initialize Firebase", e);
                throw new RuntimeException("Could not initialize Firebase", e);
            }
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
            try {
                serviceAccount = new FileInputStream(firebaseConfigPath);
            } catch (IOException e) {
                // File not found, use default credentials (for cloud environments)
                log.info("No service account file found at {}, using default credentials", firebaseConfigPath);
                return GoogleCredentials.getApplicationDefault();
            }
        }
        
        return GoogleCredentials.fromStream(serviceAccount);
    }

    @Bean(destroyMethod = "")
    public Firestore firestore() {
        synchronized (FirebaseConfig.class) {
            // Ensure Firebase app is initialized
            if (FirebaseApp.getApps().isEmpty()) {
                try {
                    FirebaseOptions options = buildFirebaseOptions();
                    firebaseApp = FirebaseApp.initializeApp(options);
                    log.info("Firebase initialized in firestore() bean for project: {}", projectId);
                } catch (IOException e) {
                    log.error("Failed to initialize Firebase in firestore() bean", e);
                    throw new RuntimeException("Could not initialize Firebase", e);
                }
            } else if (firebaseApp == null) {
                firebaseApp = FirebaseApp.getInstance();
            }
            
            Firestore firestore = FirestoreClient.getFirestore();
            
            // Verify the client is not closed
            if (firestore == null) {
                throw new IllegalStateException("Firestore client is null");
            }
            
            log.debug("Firestore client obtained successfully");
            return firestore;
        }
    }
}
