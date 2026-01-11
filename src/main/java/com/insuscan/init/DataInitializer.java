package com.insuscan.init;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.insuscan.crud.MealRepository;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.MealEntity;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.enums.SyringeType;
import com.insuscan.enums.UserRole;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

// Run with: -Dspring.profiles.active=InitData
@Component
@Profile("InitData")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MealRepository mealRepository;

    @Value("${spring.application.name}")
    private String systemId;

    public DataInitializer(UserRepository userRepository, MealRepository mealRepository) {
        this.userRepository = userRepository;
        this.mealRepository = mealRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== InsuScan Data Initializer (Firebase) ===");
        System.out.println("SystemId: " + systemId);

        // Clear existing data
        mealRepository.deleteAll();
        userRepository.deleteAll();
        System.out.println("Cleared existing data");

        // Create demo users
        createDemoUsers();

        // Create demo meals
        createDemoMeals();

        System.out.println("=== Data initialization complete ===");
    }

    private void createDemoUsers() {
        // Admin user
        UserEntity admin = new UserEntity();
        admin.setId(systemId + "_admin@insuscan.com");
        admin.setRole(UserRole.ADMIN);
        admin.setUserName("Admin");
        admin.setAvatar("admin_avatar");
        admin.setCreatedAt(new Date());
        admin.setUpdatedAt(new Date());
        userRepository.save(admin);
        System.out.println("Created admin user: admin@insuscan.com");

        // Patient - Daniel
        UserEntity daniel = new UserEntity();
        daniel.setId(systemId + "_daniel@test.com");
        daniel.setRole(UserRole.PATIENT);
        daniel.setUserName("Daniel");
        daniel.setAvatar("daniel_avatar");
        daniel.setInsulinCarbRatio(0.1f);   // 1:10 ratio
        daniel.setCorrectionFactor(50f);    // 50 mg/dL per unit
        daniel.setTargetGlucose(100);       // 100 mg/dL target
        daniel.setSyringeType(SyringeType.SYRINGE_30_UNIT);
        daniel.setCreatedAt(new Date());
        daniel.setUpdatedAt(new Date());
        userRepository.save(daniel);
        System.out.println("Created patient: daniel@test.com");

        // Patient - Nimrod
        UserEntity nimrod = new UserEntity();
        nimrod.setId(systemId + "_nimrod@test.com");
        nimrod.setRole(UserRole.PATIENT);
        nimrod.setUserName("Nimrod");
        nimrod.setAvatar("nimrod_avatar");
        nimrod.setInsulinCarbRatio(0.083f); // 1:12 ratio
        nimrod.setCorrectionFactor(40f);
        nimrod.setTargetGlucose(110);
        nimrod.setSyringeType(SyringeType.INSULIN_PEN);
        nimrod.setCreatedAt(new Date());
        nimrod.setUpdatedAt(new Date());
        userRepository.save(nimrod);
        System.out.println("Created patient: nimrod@test.com");

        // Caregiver
        UserEntity caregiver = new UserEntity();
        caregiver.setId(systemId + "_parent@test.com");
        caregiver.setRole(UserRole.CAREGIVER);
        caregiver.setUserName("Parent");
        caregiver.setAvatar("caregiver_avatar");
        caregiver.setCreatedAt(new Date());
        caregiver.setUpdatedAt(new Date());
        userRepository.save(caregiver);
        System.out.println("Created caregiver: parent@test.com");
    }

    private void createDemoMeals() {
        String danielId = systemId + "_daniel@test.com";

        // Meal 1 - Completed breakfast
        MealEntity meal1 = new MealEntity();
        meal1.setId(systemId + "_" + UUID.randomUUID().toString());
        meal1.setUserId(danielId);
        meal1.setImageUrl("https://example.com/breakfast.jpg");
        meal1.setFoodItems(Arrays.asList(
            createFoodItem("Toast", "טוסט", 30f, 15f, 80f, 3f, 1f, 0.92f),
            createFoodItem("Scrambled eggs", "ביצה מקושקשת", 100f, 1f, 150f, 12f, 10f, 0.95f),
            createFoodItem("Orange juice", "מיץ תפוזים", 200f, 22f, 90f, 1f, 0f, 0.88f)
        ));
        meal1.setTotalCarbs(38f);
        meal1.setEstimatedWeight(330f);
        meal1.setAnalysisConfidence(0.85f);
        meal1.setReferenceDetected(true);
        meal1.setRecommendedDose(4f);
        meal1.setActualDose(4f);
        meal1.setStatus(MealStatus.COMPLETED);
        meal1.setScannedAt(new Date(System.currentTimeMillis() - 86400000)); // Yesterday
        meal1.setConfirmedAt(new Date(System.currentTimeMillis() - 86400000));
        meal1.setCompletedAt(new Date(System.currentTimeMillis() - 86400000));
        mealRepository.save(meal1);
        System.out.println("Created completed meal: breakfast");

        // Meal 2 - Confirmed lunch
        MealEntity meal2 = new MealEntity();
        meal2.setId(systemId + "_" + UUID.randomUUID().toString());
        meal2.setUserId(danielId);
        meal2.setImageUrl("https://example.com/lunch.jpg");
        meal2.setFoodItems(Arrays.asList(
            createFoodItem("Chicken breast", "חזה עוף", 150f, 0f, 165f, 31f, 4f, 0.94f),
            createFoodItem("Rice", "אורז", 150f, 45f, 195f, 4f, 1f, 0.91f),
            createFoodItem("Mixed vegetables", "ירקות מעורבים", 100f, 8f, 50f, 2f, 0f, 0.87f)
        ));
        meal2.setTotalCarbs(53f);
        meal2.setEstimatedWeight(400f);
        meal2.setPlateDiameterCm(24f);
        meal2.setPlateDepthCm(3f);
        meal2.setAnalysisConfidence(0.89f);
        meal2.setReferenceDetected(true);
        meal2.setRecommendedDose(5.5f);
        meal2.setActualDose(5f);
        meal2.setStatus(MealStatus.CONFIRMED);
        meal2.setScannedAt(new Date(System.currentTimeMillis() - 3600000)); // 1 hour ago
        meal2.setConfirmedAt(new Date(System.currentTimeMillis() - 3600000));
        mealRepository.save(meal2);
        System.out.println("Created confirmed meal: lunch");

        // Meal 3 - Pending dinner
        MealEntity meal3 = new MealEntity();
        meal3.setId(systemId + "_" + UUID.randomUUID().toString());
        meal3.setUserId(danielId);
        meal3.setImageUrl("https://example.com/dinner.jpg");
        meal3.setFoodItems(Arrays.asList(
            createFoodItem("Pasta", "פסטה", 200f, 60f, 290f, 10f, 2f, 0.93f),
            createFoodItem("Tomato sauce", "רוטב עגבניות", 80f, 8f, 35f, 1f, 0f, 0.90f)
        ));
        meal3.setTotalCarbs(68f);
        meal3.setEstimatedWeight(280f);
        meal3.setAnalysisConfidence(0.86f);
        meal3.setReferenceDetected(false);
        meal3.setStatus(MealStatus.PENDING);
        meal3.setScannedAt(new Date()); // Now
        mealRepository.save(meal3);
        System.out.println("Created pending meal: dinner");

        System.out.println("Created 3 demo meals");
    }

    private MealEntity.FoodItem createFoodItem(
            String name, String nameHebrew, Float quantity, 
            Float carbs, Float confidence) {
        MealEntity.FoodItem item = new MealEntity.FoodItem();
        item.setName(name);
        item.setNameHebrew(nameHebrew);
        item.setQuantity(quantity);
        item.setCarbs(carbs);
        item.setConfidence(confidence);
        return item;
    }
}
