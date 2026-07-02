package com.insuscan.crud;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.insuscan.data.InsulinPlan;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Firestore-backed repository for {@link UserEntity}, providing CRUD operations,
 * pagination, and manual mapping to/from Firestore documents.
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private static final String COLLECTION_NAME = "users";

    private final Firestore firestore;

    public UserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

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

    public boolean existsById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME).document(id).get().get();
            return doc.exists();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking user existence: {}", id, e);
            throw new RuntimeException("Failed to check user existence", e);
        }
    }

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

    public void deleteById(String id) {
        try {
            firestore.collection(COLLECTION_NAME).document(id).delete().get();
            log.debug("Deleted user: {}", id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting user: {}", id, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

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

    private List<UserEntity> executeQuery(Query query) throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = query.get().get();
        return snapshot.getDocuments().stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
    }

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

    private Map<String, Object> entityToMap(UserEntity entity) {
        Map<String, Object> map = new HashMap<>();

        map.put("id", entity.getId());
        map.put("role", entity.getRole() != null ? entity.getRole().name() : null);
        map.put("userName", entity.getUserName());
        map.put("avatar", entity.getAvatar());

        map.put("insulinCarbRatio", entity.getInsulinCarbRatio());
        map.put("correctionFactor", entity.getCorrectionFactor());
        map.put("targetGlucose", entity.getTargetGlucose());

        map.put("age", entity.getAge());
        map.put("gender", entity.getGender());

        map.put("doseRounding", entity.getDoseRounding());

        if (entity.getInsulinPlans() != null) {
            List<Map<String, Object>> plansList = new ArrayList<>();
            for (InsulinPlan plan : entity.getInsulinPlans()) {
                Map<String, Object> planMap = new HashMap<>();
                planMap.put("id", plan.getId());
                planMap.put("name", plan.getName());
                planMap.put("isDefault", plan.isDefault());
                planMap.put("icr", plan.getIcr());
                planMap.put("isf", plan.getIsf());
                planMap.put("targetGlucose", plan.getTargetGlucose());
                plansList.add(planMap);
            }
            map.put("insulinPlans", plansList);
        }

        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());

        return map;
    }

    private UserEntity mapToEntity(DocumentSnapshot doc) {
        UserEntity entity = new UserEntity();

        entity.setId(doc.getString("id"));

        String roleStr = doc.getString("role");
        if (roleStr != null) {
            entity.setRole(UserRole.valueOf(roleStr));
        }

        entity.setUserName(doc.getString("userName"));
        entity.setAvatar(doc.getString("avatar"));

        Double insulinRatio = doc.getDouble("insulinCarbRatio");
        entity.setInsulinCarbRatio(insulinRatio != null ? insulinRatio.floatValue() : null);

        Double correctionFactor = doc.getDouble("correctionFactor");
        entity.setCorrectionFactor(correctionFactor != null ? correctionFactor.floatValue() : null);

        Long targetGlucose = doc.getLong("targetGlucose");
        entity.setTargetGlucose(targetGlucose != null ? targetGlucose.intValue() : null);

        Long age = doc.getLong("age");
        entity.setAge(age != null ? age.intValue() : null);

        entity.setGender(doc.getString("gender"));

        entity.setDoseRounding(doc.getString("doseRounding"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plansRaw = (List<Map<String, Object>>) doc.get("insulinPlans");
        if (plansRaw != null) {
            List<InsulinPlan> plans = new ArrayList<>();
            for (Map<String, Object> planMap : plansRaw) {
                InsulinPlan plan = new InsulinPlan();
                plan.setId((String) planMap.get("id"));
                plan.setName((String) planMap.get("name"));
                plan.setDefault(Boolean.TRUE.equals(planMap.get("isDefault")));
                Number icr = (Number) planMap.get("icr");
                if (icr != null) plan.setIcr(icr.floatValue());
                Number isf = (Number) planMap.get("isf");
                if (isf != null) plan.setIsf(isf.floatValue());
                Number tg = (Number) planMap.get("targetGlucose");
                if (tg != null) plan.setTargetGlucose(tg.intValue());
                plans.add(plan);
            }
            entity.setInsulinPlans(plans);
        }

        entity.setCreatedAt(doc.getDate("createdAt"));
        entity.setUpdatedAt(doc.getDate("updatedAt"));

        return entity;
    }
}