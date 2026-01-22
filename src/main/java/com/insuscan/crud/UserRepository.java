package com.insuscan.crud;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class UserRepository {
    
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private static final String COLLECTION_NAME = "users";
    
    private final Firestore firestore;

    public UserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Save or update a user
    public UserEntity save(UserEntity user) {
        try {
            user.setUpdatedAt(new Date());
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(user.getId());
            docRef.set(entityToMap(user)).get();
            log.debug("Saved user: {}", user.getId());
            return user;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving user: {}", user.getId(), e);
            throw new RuntimeException("Failed to save user", e);
        }
    }

    // Find user by ID
    public Optional<UserEntity> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(mapToEntity(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding user: {}", id, e);
            throw new RuntimeException("Failed to find user", e);
        }
    }

    // Check if user exists
    public boolean existsById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME).document(id).get().get();
            return doc.exists();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking user existence: {}", id, e);
            throw new RuntimeException("Failed to check user existence", e);
        }
    }

    // Find all users with pagination
    public List<UserEntity> findAll(int page, int size) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .offset(page * size)
                    .limit(size);
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding all users", e);
            throw new RuntimeException("Failed to find users", e);
        }
    }

    // Find users by role with pagination
    public List<UserEntity> findByRole(UserRole role, int page, int size) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("role", role.name())
                    .offset(page * size)
                    .limit(size);
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding users by role: {}", role, e);
            throw new RuntimeException("Failed to find users by role", e);
        }
    }

    // Find all users by role
    public List<UserEntity> findByRole(UserRole role) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("role", role.name());
            
            return executeQuery(query);
        } catch (Exception e) {
            log.error("Error finding users by role: {}", role, e);
            throw new RuntimeException("Failed to find users by role", e);
        }
    }

    // Delete user by ID
    public void deleteById(String id) {
        try {
            firestore.collection(COLLECTION_NAME).document(id).delete().get();
            log.debug("Deleted user: {}", id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting user: {}", id, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    // Delete all users
    public void deleteAll() {
        try {
            CollectionReference collection = firestore.collection(COLLECTION_NAME);
            deleteCollection(collection);
            log.info("Deleted all users");
        } catch (Exception e) {
            log.error("Error deleting all users", e);
            throw new RuntimeException("Failed to delete all users", e);
        }
    }

    // Count all users
    public long count() {
        try {
            AggregateQuerySnapshot snapshot = firestore.collection(COLLECTION_NAME)
                    .count()
                    .get()
                    .get();
            return snapshot.getCount();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error counting users", e);
            throw new RuntimeException("Failed to count users", e);
        }
    }

    // Helper: execute query and return list
    private List<UserEntity> executeQuery(Query query) throws ExecutionException, InterruptedException {
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
    private Map<String, Object> entityToMap(UserEntity entity) {
        Map<String, Object> map = new HashMap<>();
        
        // Base fields
        map.put("id", entity.getId());
        map.put("role", entity.getRole() != null ? entity.getRole().name() : null);
        map.put("userName", entity.getUserName());
        map.put("avatar", entity.getAvatar());
        
        // Medical profile
        map.put("insulinCarbRatio", entity.getInsulinCarbRatio());
        map.put("correctionFactor", entity.getCorrectionFactor());
        map.put("targetGlucose", entity.getTargetGlucose());
        
        // Syringe settings
        map.put("syringeType", entity.getSyringeType() != null ? entity.getSyringeType().name() : null);
        map.put("customSyringeLength", entity.getCustomSyringeLength());
        
        // Personal info
        map.put("age", entity.getAge());
        map.put("gender", entity.getGender());
        map.put("pregnant", entity.getPregnant());
        map.put("dueDate", entity.getDueDate());
        
        // Medical info
        map.put("diabetesType", entity.getDiabetesType());
        map.put("insulinType", entity.getInsulinType());
        map.put("activeInsulinTime", entity.getActiveInsulinTime());
        
        // Dose settings
        map.put("doseRounding", entity.getDoseRounding());
        
        // Adjustment factors
        map.put("sickDayAdjustment", entity.getSickDayAdjustment());
        map.put("stressAdjustment", entity.getStressAdjustment());
        map.put("lightExerciseAdjustment", entity.getLightExerciseAdjustment());
        map.put("intenseExerciseAdjustment", entity.getIntenseExerciseAdjustment());
        
        // Preferences
        map.put("glucoseUnits", entity.getGlucoseUnits());
        
        // Timestamps
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        
        return map;
    }

    // Helper: convert Firestore document to entity
    private UserEntity mapToEntity(DocumentSnapshot doc) {
        UserEntity entity = new UserEntity();
        
        // Base fields
        entity.setId(doc.getString("id"));
        
        String roleStr = doc.getString("role");
        if (roleStr != null) {
            entity.setRole(UserRole.valueOf(roleStr));
        }
        
        entity.setUserName(doc.getString("userName"));
        entity.setAvatar(doc.getString("avatar"));
        
        // Medical profile
        Double insulinRatio = doc.getDouble("insulinCarbRatio");
        entity.setInsulinCarbRatio(insulinRatio != null ? insulinRatio.floatValue() : null);
        
        Double correctionFactor = doc.getDouble("correctionFactor");
        entity.setCorrectionFactor(correctionFactor != null ? correctionFactor.floatValue() : null);
        
        Long targetGlucose = doc.getLong("targetGlucose");
        entity.setTargetGlucose(targetGlucose != null ? targetGlucose.intValue() : null);
        
        // Syringe settings
        String syringeStr = doc.getString("syringeType");
        if (syringeStr != null) {
            entity.setSyringeType(com.insuscan.enums.SyringeType.valueOf(syringeStr));
        }
        
        Double customLength = doc.getDouble("customSyringeLength");
        entity.setCustomSyringeLength(customLength != null ? customLength.floatValue() : null);
        
        // Personal info
        Long age = doc.getLong("age");
        entity.setAge(age != null ? age.intValue() : null);
        
        entity.setGender(doc.getString("gender"));
        entity.setPregnant(doc.getBoolean("pregnant"));
        entity.setDueDate(doc.getString("dueDate"));
        
        // Medical info
        entity.setDiabetesType(doc.getString("diabetesType"));
        entity.setInsulinType(doc.getString("insulinType"));
        
        Long activeInsulin = doc.getLong("activeInsulinTime");
        entity.setActiveInsulinTime(activeInsulin != null ? activeInsulin.intValue() : null);
        
        // Dose settings
        entity.setDoseRounding(doc.getString("doseRounding"));
        
        // Adjustment factors
        Long sickDayAdj = doc.getLong("sickDayAdjustment");
        entity.setSickDayAdjustment(sickDayAdj != null ? sickDayAdj.intValue() : null);
        
        Long stressAdj = doc.getLong("stressAdjustment");
        entity.setStressAdjustment(stressAdj != null ? stressAdj.intValue() : null);
        
        Long lightExAdj = doc.getLong("lightExerciseAdjustment");
        entity.setLightExerciseAdjustment(lightExAdj != null ? lightExAdj.intValue() : null);
        
        Long intenseExAdj = doc.getLong("intenseExerciseAdjustment");
        entity.setIntenseExerciseAdjustment(intenseExAdj != null ? intenseExAdj.intValue() : null);
        
        // Preferences
        entity.setGlucoseUnits(doc.getString("glucoseUnits"));
        
        // Timestamps
        entity.setCreatedAt(doc.getDate("createdAt"));
        entity.setUpdatedAt(doc.getDate("updatedAt"));
        
        return entity;
    }
}