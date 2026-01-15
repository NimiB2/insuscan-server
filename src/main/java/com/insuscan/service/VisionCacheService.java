package com.insuscan.service;

import com.insuscan.boundary.FoodRecognitionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches vision analysis results by image hash to ensure consistency
 * for repeated scans of the same image.
 */
@Service
public class VisionCacheService {

    private static final Logger log = LoggerFactory.getLogger(VisionCacheService.class);
    
    // Cache: image hash -> vision result
    private final Map<String, FoodRecognitionResult> cache = new ConcurrentHashMap<>();
    
    // Cache: image hash -> cached timestamp (for expiration)
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    
    // Cache expiration: 24 hours
    private static final long CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000;

    /**
     * Get cached result or null if not found/expired
     */
    public FoodRecognitionResult getCached(String imageHash) {
        if (imageHash == null) {
            return null;
        }
        
        FoodRecognitionResult cached = cache.get(imageHash);
        if (cached == null) {
            return null;
        }
        
        // Check expiration
        Long timestamp = cacheTimestamps.get(imageHash);
        if (timestamp != null && System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS) {
            cache.remove(imageHash);
            cacheTimestamps.remove(imageHash);
            log.debug("Cache expired for image hash: {}", imageHash.substring(0, 8) + "...");
            return null;
        }
        
        log.info("Using cached vision result for image hash: {}...", imageHash.substring(0, 8));
        return cached;
    }

    /**
     * Store result in cache
     */
    public void putCache(String imageHash, FoodRecognitionResult result) {
        if (imageHash != null && result != null) {
            cache.put(imageHash, result);
            cacheTimestamps.put(imageHash, System.currentTimeMillis());
            log.debug("Cached vision result for image hash: {}...", imageHash.substring(0, 8));
        }
    }

    /**
     * Generate hash from base64 image string
     */
    public String hashImage(String base64Image) {
        try {
            // Remove data URL prefix if present
            String imageData = base64Image;
            if (imageData.contains(",")) {
                imageData = imageData.substring(imageData.indexOf(",") + 1);
            }
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            byte[] hashBytes = digest.digest(imageBytes);
            
            // Return first 16 bytes as hex string (sufficient for uniqueness)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(16, hashBytes.length); i++) {
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to hash image", e);
            // Fallback: use first 32 chars of base64 as hash
            return base64Image.substring(0, Math.min(32, base64Image.length()));
        }
    }

    /**
     * Clear expired entries (can be called periodically)
     */
    public void clearExpired() {
        long now = System.currentTimeMillis();
        cacheTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > CACHE_EXPIRATION_MS) {
                cache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
