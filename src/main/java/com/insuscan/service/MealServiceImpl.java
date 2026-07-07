package com.insuscan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.calculation.CalculationParams;
import com.insuscan.calculation.CalculationResult;
import com.insuscan.calculation.InsulinCalculator;
import com.insuscan.converter.MealConverter;
import com.insuscan.crud.MealRepository;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.MealEntity;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.enums.UserRole;
import com.insuscan.exception.InsuScanInvalidInputException;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.exception.InsuScanUnauthorizedException;
import com.insuscan.util.InputValidators;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service implementation for meal management: creation from scans, food item
 * updates, confirmation with insulin calculation, portion analysis updates,
 * and admin-level deletes.
 */
@Service
public class MealServiceImpl implements MealService {

    private static final Logger log = LoggerFactory.getLogger(MealServiceImpl.class);

    private final MealRepository mealRepository;
    private final UserRepository userRepository;
    private final MealConverter mealConverter;

    @Value("${spring.application.name}")
    private String systemId;

    public MealServiceImpl(MealRepository mealRepository, UserRepository userRepository, MealConverter mealConverter) {
        this.mealRepository = mealRepository;
        this.userRepository = userRepository;
        this.mealConverter = mealConverter;
    }

    @Override
    public MealBoundary createMeal(String userSystemId, String userEmail, String imageUrl) {
        InputValidators.validateSystemId(userSystemId);
        InputValidators.validateEmail(userEmail);

        String userId = userSystemId + "_" + userEmail;
        if (!userRepository.existsById(userId)) {
            throw new InsuScanNotFoundException("User not found: " + userEmail);
        }

        MealEntity entity = mealConverter.createNewMealEntity(userEmail, imageUrl);
        MealEntity saved = mealRepository.save(entity);
        return mealConverter.toBoundary(saved);
    }

    @Override
    public MealBoundary saveScannedMeal(String systemId, String email, MealBoundary mealBoundary) {
        InputValidators.validateSystemId(systemId);
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("User email is required");
        }

        String cleanEmail = email.toLowerCase().trim();
        MealEntity entity = mealConverter.toEntity(mealBoundary);

        if (entity.getId() == null || entity.getId().isEmpty()) {
            entity.setId(systemId + "_" + UUID.randomUUID().toString());
        }

        String fullUserId = systemId + "_" + cleanEmail;
        entity.setUserId(fullUserId);

        log.debug("Saving meal for user: {} (email: {})", entity.getUserId(), cleanEmail);
        log.debug("Carb dose: {}, correction dose: {}, saved plan: {}",
                entity.getCarbDose(), entity.getCorrectionDose(), entity.getSavedPlanName());

        entity.setStatus(MealStatus.CONFIRMED);
        entity.setConfirmedAt(new Date());

        if (entity.getScannedAt() == null) {
            entity.setScannedAt(new Date());
        }

        MealEntity saved = mealRepository.save(entity);

        log.debug("After save - carb: {}, correction: {}, plan: {}",
                saved.getCarbDose(), saved.getCorrectionDose(), saved.getSavedPlanName());

        return mealConverter.toBoundary(saved);
    }

    @Override
    public Optional<MealBoundary> getMealById(String systemId, String mealId) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        return mealRepository.findById(id)
                .map(mealConverter::toBoundary);
    }

    @Override
    public List<MealBoundary> getMealsByUser(String systemId, String email, int page, int size) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String cleanEmail = email.toLowerCase().trim();
        String userId = systemId + "_" + cleanEmail;
        log.debug("Fetching meals for user: {} (email: {})", userId, cleanEmail);

        List<MealBoundary> results = mealRepository.findByUserId(userId, page, size)
                .stream()
                .filter(meal -> meal.getStatus() != MealStatus.PENDING)
                .map(mealConverter::toBoundary)
                .collect(Collectors.toList());

        log.debug("Found {} meals", results.size());
        return results;
    }

    @Override
    public MealBoundary updateFoodItems(String systemId, String mealId, List<FoodItemBoundary> foodItems) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        MealEntity entity = mealRepository.findById(id)
                .orElseThrow(() -> new InsuScanNotFoundException("Meal not found: " + mealId));

        if (foodItems != null) {
            List<MealEntity.FoodItem> items = foodItems.stream()
                    .map(mealConverter::foodItemToEntity)
                    .collect(Collectors.toList());
            entity.setFoodItems(items);
        }

        mealConverter.calculateTotals(entity);

        MealEntity saved = mealRepository.save(entity);
        return mealConverter.toBoundary(saved);
    }

    @Override
    public MealBoundary confirmMeal(String systemId, String mealId, Float actualDose, Integer currentGlucose) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        MealEntity meal = mealRepository.findById(id)
                .orElseThrow(() -> new InsuScanNotFoundException("Meal not found: " + mealId));

        UserEntity user = userRepository.findById(meal.getUserId())
                .orElseThrow(() -> new InsuScanNotFoundException("User not found for meal"));

        meal.setCurrentGlucose(currentGlucose);

        Float totalCarbs = meal.getTotalCarbs();
        if (totalCarbs != null && totalCarbs > 0) {
            InsulinCalculator calculator = new InsulinCalculator();
            CalculationParams params = new CalculationParams.Builder()
                    .withTotalCarbs(totalCarbs)
                    .withInsulinCarbRatio(user.getInsulinCarbRatio())
                    .withCorrectionFactor(user.getCorrectionFactor())
                    .withTargetGlucose(user.getTargetGlucose())
                    .withCurrentGlucose(currentGlucose)
                    .build();
            CalculationResult result = calculator.calculate(params);

            meal.setCarbDose(result.getCarbDose());
            meal.setCorrectionDose(result.getCorrectionDose());
            meal.setRecommendedDose(result.getRoundedDose());
            meal.setProfileComplete(result.isProfileComplete());

            if (!result.isProfileComplete()) {
                meal.setInsulinMessage("Profile incomplete - using default values");
            }
        }

        if (actualDose != null) {
            if (!InputValidators.isValidInsulinDose(actualDose)) {
                throw new InsuScanInvalidInputException(
                        "Invalid insulin dose: " + actualDose + ". Must be between 0 and 100 units.");
            }
            meal.setActualDose(actualDose);
        } else if (meal.getRecommendedDose() != null) {
            meal.setActualDose(meal.getRecommendedDose());
        }

        meal.setStatus(MealStatus.CONFIRMED);
        meal.setConfirmedAt(new Date());

        MealEntity saved = mealRepository.save(meal);
        return mealConverter.toBoundary(saved);
    }

    @Override
    public MealBoundary completeMeal(String systemId, String mealId) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        MealEntity meal = mealRepository.findById(id)
                .orElseThrow(() -> new InsuScanNotFoundException("Meal not found: " + mealId));

        if (meal.getStatus() != MealStatus.CONFIRMED) {
            throw new InsuScanInvalidInputException(
                    "Meal must be confirmed before completion. Current status: " + meal.getStatus());
        }

        meal.setStatus(MealStatus.COMPLETED);
        meal.setCompletedAt(new Date());

        MealEntity saved = mealRepository.save(meal);
        return mealConverter.toBoundary(saved);
    }

    @Override
    public MealBoundary updatePortionAnalysis(String systemId, String mealId,
            Float estimatedWeight, Float volumeCm3,
            Float diameterCm, Float depthCm,
            Float confidence, Boolean refDetected) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        MealEntity meal = mealRepository.findById(id)
                .orElseThrow(() -> new InsuScanNotFoundException("Meal not found: " + mealId));

        if (estimatedWeight != null) meal.setEstimatedWeight(estimatedWeight);
        if (volumeCm3 != null) meal.setPlateVolumeCm3(volumeCm3);
        if (diameterCm != null) meal.setPlateDiameterCm(diameterCm);
        if (depthCm != null) meal.setPlateDepthCm(depthCm);
        if (confidence != null) meal.setAnalysisConfidence(confidence);
        if (refDetected != null) meal.setReferenceDetected(refDetected);

        MealEntity saved = mealRepository.save(meal);
        return mealConverter.toBoundary(saved);
    }

    @Override
    public void deleteMeal(String systemId, String mealId) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        if (!mealRepository.existsById(id)) {
            throw new InsuScanNotFoundException("Meal not found: " + mealId);
        }

        mealRepository.deleteById(id);
    }

    @Override
    public void deleteAllMealsForUser(String adminSystemId, String adminEmail,
            String targetSystemId, String targetEmail) {
        verifyAdminAccess(adminSystemId, adminEmail);

        String targetUserId = targetSystemId + "_" + targetEmail;
        mealRepository.deleteByUserId(targetUserId);
    }

    @Override
    public void deleteAllMeals(String adminSystemId, String adminEmail) {
        verifyAdminAccess(adminSystemId, adminEmail);
        mealRepository.deleteAll();
    }

    @Override
    public List<MealBoundary> getRecentMeals(String systemId, String email, int count) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String userId = userIdFor(email);

        return mealRepository.findRecentByUserId(userId, count)
                .stream()
                .filter(meal -> meal.getStatus() != MealStatus.PENDING)
                .map(mealConverter::toBoundary)
                .collect(Collectors.toList());
    }

    @Override
    public long getMealCountForUser(String systemId, String email) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String userId = userIdFor(email);
        return mealRepository.countByUserId(userId);
    }

    @Override
    public List<MealBoundary> getMealsByDateRange(String systemId, String email,
            LocalDate from, LocalDate to,
            int page, int size) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String userId = userIdFor(email);

        Date fromDate = Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date toDate = Date.from(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        return mealRepository.findByUserIdAndDateRange(userId, fromDate, toDate, page, size)
                .stream()
                .filter(meal -> meal.getStatus() != MealStatus.PENDING)
                .map(mealConverter::toBoundary)
                .collect(Collectors.toList());
    }

    private String userIdFor(String email) {
        return systemId + "_" + email.toLowerCase().trim();
    }

    private void verifyAdminAccess(String systemId, String email) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String id = systemId + "_" + email;
        UserEntity admin = userRepository.findById(id)
                .orElseThrow(() -> new InsuScanNotFoundException("Admin user not found: " + email));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new InsuScanUnauthorizedException(
                    "User does not have admin privileges: " + email);
        }
    }
}