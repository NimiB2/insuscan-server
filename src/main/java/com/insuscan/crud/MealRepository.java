package com.insuscan.crud;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.insuscan.data.MealEntity;
import com.insuscan.enums.MealStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class MealRepository {
    
    private static final Logger log = LoggerFactory.getLogger(MealRepository.class);
    private static final String COLLECTION_NAME = "meals";
    
    private final Firestore firestore;

    public MealRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Save or update a meal
    public MealEntity save(MealEntity meal) {
        try {
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(meal.getId());
            docRef.set(entityToMap(meal)).get();
            log.debug("Saved meal: {}", meal.getId());
            return meal;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving meal: {}", meal.getId(), e);
            throw new RuntimeException("Failed to save meal", e);
        }
    }

    // Find meal by ID
    public Optional<MealEntity> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(mapToEntity(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding meal: {}", id, e);
            throw new RuntimeException("Failed to find meal", e);
        }
    }

    // Check if meal exists
    public boolean existsById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME).document(id).get().get();
            return doc.exists();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking meal existence: {}", id, e);
            throw new RuntimeException("Failed to check meal existence", e);
        }
    }

    // Find meals by user ID with pagination
    public List<MealEntity> findByUserId(String userId, int page, int size) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .orderBy("scannedAt", Query.Direction.DESCENDING)
                    .offset(page * size)
                    .limit(size);
            
            return executeQuery(query);
        } catch (Exception e) {
            // If index error, fall back to query without orderBy and sort in memory
            if (e.getMessage() != null && e.getMessage().contains("index")) {
                log.warn("Composite index not found for paginated query, using fallback query (slower). userId={}", userId);

                try {
                    // Firestore has no server-side offset without ordering; emulate it:
                    // fetch enough items, sort, then slice.
                    int offset = Math.max(0, page * size);
                    int fetchLimit = Math.min(1000, offset + size); // safety cap

                    Query fallbackQuery = firestore.collection(COLLECTION_NAME)
                            .whereEqualTo("userId", userId)
                            .limit(fetchLimit);

                    List<MealEntity> meals = executeQuery(fallbackQuery);

                    // Sort by scannedAt descending in memory
                    meals.sort((a, b) -> {
                        if (a.getScannedAt() == null && b.getScannedAt() == null) return 0;
                        if (a.getScannedAt() == null) return 1;
                        if (b.getScannedAt() == null) return -1;
                        return b.getScannedAt().compareTo(a.getScannedAt());
                    });

                    // Slice page
                    if (offset >= meals.size()) return List.of();
                    int toIndex = Math.min(meals.size(), offset + size);
                    return meals.subList(offset, toIndex);
                } catch (Exception fallbackError) {
                    log.error("Fallback paginated query also failed for user: {}", userId, fallbackError);
                    throw new RuntimeException("Failed to find meals (fallback)", fallbackError);
                }
            }

            log.error("Error finding meals by user: {}", userId, e);
            throw new RuntimeException("Failed to find meals", e);
        }
    }

    // Find all meals by user ID
    public List<MealEntity> findByUserId(String userId) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .orderBy("scannedAt", Query.Direction.DESCENDING);
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding meals by user: {}", userId, e);
            throw new RuntimeException("Failed to find meals", e);
        }
    }

    // Find recent meals by user (ordered by scanned date desc)
    public List<MealEntity> findRecentByUserId(String userId, int limit) {
        try {
            // Try query with orderBy first (requires composite index)
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .orderBy("scannedAt", Query.Direction.DESCENDING)
                    .limit(limit);
            
            return executeQuery(query);
        } catch (Exception e) {
            // If index error, fall back to query without orderBy and sort in memory
            if (e.getMessage() != null && e.getMessage().contains("index")) {
                log.warn("Composite index not found, using fallback query (slower). Create index at: {}", 
                        e.getMessage().contains("create it here") ? "Firebase Console" : "Firebase Console");
                
                try {
                    // Fallback: query without orderBy, then sort in memory
                    Query fallbackQuery = firestore.collection(COLLECTION_NAME)
                            .whereEqualTo("userId", userId)
                            .limit(limit * 2); // Get more to account for no ordering
                    
                    List<MealEntity> meals = executeQuery(fallbackQuery);
                    
                    // Sort by scannedAt descending in memory
                    meals.sort((a, b) -> {
                        if (a.getScannedAt() == null && b.getScannedAt() == null) return 0;
                        if (a.getScannedAt() == null) return 1;
                        if (b.getScannedAt() == null) return -1;
                        return b.getScannedAt().compareTo(a.getScannedAt());
                    });
                    
                    // Return only the requested limit
                    return meals.stream().limit(limit).collect(java.util.stream.Collectors.toList());
                } catch (Exception fallbackError) {
                    log.error("Fallback query also failed for user: {}", userId, fallbackError);
                    throw new RuntimeException("Failed to find recent meals", fallbackError);
                }
            } else {
                log.error("Error finding recent meals by user: {}", userId, e);
                throw new RuntimeException("Failed to find recent meals", e);
            }
        }
    }

    // Find meals by user and status
    public List<MealEntity> findByUserIdAndStatus(String userId, MealStatus status) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", status.name());
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding meals by user and status: {} {}", userId, status, e);
            throw new RuntimeException("Failed to find meals", e);
        }
    }

    // Find meals in date range for a user
    public List<MealEntity> findByUserIdAndDateRange(String userId, Date startDate, Date endDate) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("scannedAt", startDate)
                    .whereLessThanOrEqualTo("scannedAt", endDate)
                    .orderBy("scannedAt", Query.Direction.DESCENDING);
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding meals by date range for user: {}", userId, e);
            throw new RuntimeException("Failed to find meals", e);
        }
    }

    // Count meals by user
    public long countByUserId(String userId) {
        try {
            AggregateQuerySnapshot snapshot = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .count()
                    .get()
                    .get();
            return snapshot.getCount();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error counting meals for user: {}", userId, e);
            throw new RuntimeException("Failed to count meals", e);
        }
    }

    // Delete meal by ID
    public void deleteById(String id) {
        try {
            firestore.collection(COLLECTION_NAME).document(id).delete().get();
            log.debug("Deleted meal: {}", id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting meal: {}", id, e);
            throw new RuntimeException("Failed to delete meal", e);
        }
    }

    // Delete all meals
    public void deleteAll() {
        try {
            CollectionReference collection = firestore.collection(COLLECTION_NAME);
            deleteCollection(collection);
            log.info("Deleted all meals");
        } catch (Exception e) {
            log.error("Error deleting all meals", e);
            throw new RuntimeException("Failed to delete all meals", e);
        }
    }

    // Delete all meals by user
    public void deleteByUserId(String userId) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId);
            
            QuerySnapshot snapshot = query.get().get();
            WriteBatch batch = firestore.batch();
            
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            
            batch.commit().get();
            log.debug("Deleted all meals for user: {}", userId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting meals for user: {}", userId, e);
            throw new RuntimeException("Failed to delete meals", e);
        }
    }

    // Find recent meals (all users, ordered by scanned date desc)
    public List<MealEntity> findAllRecent(int limit) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .orderBy("scannedAt", Query.Direction.DESCENDING)
                    .limit(limit);
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding recent meals", e);
            throw new RuntimeException("Failed to find recent meals", e);
        }
    }

    // Helper: execute query and return list
    private List<MealEntity> executeQuery(Query query) throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = query.get().get();
        return snapshot.getDocuments().stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
    }

    // Helper: delete collection in batches
    private void deleteCollection(CollectionReference collection) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = collection.limit(500).get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        
        while (!docs.isEmpty()) {
            WriteBatch batch = firestore.batch();
            for (QueryDocumentSnapshot doc : docs) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
            
            docs = collection.limit(500).get().get().getDocuments();
        }
    }

    // Helper: convert entity to map for Firestore
    @SuppressWarnings("unchecked")
    private Map<String, Object> entityToMap(MealEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entity.getId());
        map.put("userId", entity.getUserId());
        map.put("imageUrl", entity.getImageUrl());
        
        // Convert food items to list of maps
        if (entity.getFoodItems() != null) {
            List<Map<String, Object>> foodItemMaps = entity.getFoodItems().stream()
                    .map(this::foodItemToMap)
                    .collect(Collectors.toList());
            map.put("foodItems", foodItemMaps);
        }
        
        map.put("totalCarbs", entity.getTotalCarbs());
        map.put("estimatedWeight", entity.getEstimatedWeight());
        map.put("plateVolumeCm3", entity.getPlateVolumeCm3());
        map.put("plateDiameterCm", entity.getPlateDiameterCm());
        map.put("plateDepthCm", entity.getPlateDepthCm());
        map.put("analysisConfidence", entity.getAnalysisConfidence());
        map.put("referenceDetected", entity.getReferenceDetected());
        map.put("recommendedDose", entity.getRecommendedDose());
        map.put("actualDose", entity.getActualDose());
        map.put("status", entity.getStatus() != null ? entity.getStatus().name() : null);
        map.put("scannedAt", entity.getScannedAt());
        map.put("confirmedAt", entity.getConfirmedAt());
        map.put("completedAt", entity.getCompletedAt());
        
        return map;
    }

    private Map<String, Object> foodItemToMap(MealEntity.FoodItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", item.getName());
        map.put("nameHebrew", item.getNameHebrew());
        map.put("quantity", item.getQuantity());
        map.put("carbs", item.getCarbs());
        map.put("confidence", item.getConfidence());
        map.put("usdaFdcId", item.getUsdaFdcId());
        return map;
    }

    // Helper: convert Firestore document to entity
    @SuppressWarnings("unchecked")
    private MealEntity mapToEntity(DocumentSnapshot doc) {
        MealEntity entity = new MealEntity();
        entity.setId(doc.getString("id"));
        entity.setUserId(doc.getString("userId"));
        entity.setImageUrl(doc.getString("imageUrl"));
        
        // Convert food items from list of maps
        List<Map<String, Object>> foodItemMaps = (List<Map<String, Object>>) doc.get("foodItems");
        if (foodItemMaps != null) {
            List<MealEntity.FoodItem> foodItems = foodItemMaps.stream()
                    .map(this::mapToFoodItem)
                    .collect(Collectors.toList());
            entity.setFoodItems(foodItems);
        }
        
        entity.setTotalCarbs(getFloat(doc, "totalCarbs"));
        entity.setEstimatedWeight(getFloat(doc, "estimatedWeight"));
        entity.setPlateVolumeCm3(getFloat(doc, "plateVolumeCm3"));
        entity.setPlateDiameterCm(getFloat(doc, "plateDiameterCm"));
        entity.setPlateDepthCm(getFloat(doc, "plateDepthCm"));
        entity.setAnalysisConfidence(getFloat(doc, "analysisConfidence"));
        entity.setReferenceDetected(doc.getBoolean("referenceDetected"));
        entity.setRecommendedDose(getFloat(doc, "recommendedDose"));
        entity.setActualDose(getFloat(doc, "actualDose"));
        
        String statusStr = doc.getString("status");
        if (statusStr != null) {
            entity.setStatus(MealStatus.valueOf(statusStr));
        }
        
        entity.setScannedAt(doc.getDate("scannedAt"));
        entity.setConfirmedAt(doc.getDate("confirmedAt"));
        entity.setCompletedAt(doc.getDate("completedAt"));
        
        return entity;
    }

    private MealEntity.FoodItem mapToFoodItem(Map<String, Object> map) {
        MealEntity.FoodItem item = new MealEntity.FoodItem();
        item.setName((String) map.get("name"));
        item.setNameHebrew((String) map.get("nameHebrew"));
        item.setQuantity(toFloat(map.get("quantity")));
        item.setCarbs(toFloat(map.get("carbs")));
        item.setConfidence(toFloat(map.get("confidence")));
        item.setUsdaFdcId((String) map.get("usdaFdcId"));
        return item;
    }

    private Float getFloat(DocumentSnapshot doc, String field) {
        Double value = doc.getDouble(field);
        return value != null ? value.floatValue() : null;
    }

    private Float toFloat(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return ((Double) value).floatValue();
        if (value instanceof Long) return ((Long) value).floatValue();
        if (value instanceof Integer) return ((Integer) value).floatValue();
        return null;
    }
}
