package com.insuscan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
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
import com.insuscan.calculation.InsulinCalculator;
import com.insuscan.calculation.CalculationParams;
import com.insuscan.calculation.CalculationResult;
import com.insuscan.util.InputValidators;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MealServiceImpl implements MealService {

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

        // Verify user exists
        String userId = userSystemId + "_" + userEmail;
        if (!userRepository.existsById(userId)) {
            throw new InsuScanNotFoundException("User not found: " + userEmail);
        }

        // Create new meal entity
        MealEntity entity = mealConverter.createNewMealEntity(userEmail, imageUrl);
        
        MealEntity saved = mealRepository.save(entity);
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

        String userId = systemId + "_" + email;

        return mealRepository.findByUserId(userId, page, size)
            .stream()
            .map(mealConverter::toBoundary)
            .collect(Collectors.toList());
    }

    @Override
    public MealBoundary updateFoodItems(String systemId, String mealId, List<FoodItemBoundary> foodItems) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateNotEmpty(mealId, "mealId");

        String id = systemId + "_" + mealId;
        MealEntity entity = mealRepository.findById(id)
            .orElseThrow(() -> new InsuScanNotFoundException("Meal not found: " + mealId));

        // Convert food items and set on entity
        if (foodItems != null) {
            List<MealEntity.FoodItem> items = foodItems.stream()
                .map(mealConverter::foodItemToEntity)
                .collect(Collectors.toList());
            entity.setFoodItems(items);
        }

        // Recalculate totals
        mealConverter.calculateTotals(entity);

        MealEntity saved = mealRepository.save(entity);
        return mealConverter.toBoundary(saved);
    }

    @Override
	public MealBoundary confirmMeal(String systemId, String mealId, Float actualDose,
                                Integer currentGlucose, String activityLevel,
                                Boolean sickMode, Boolean stressMode) {
    InputValidators.validateSystemId(systemId);
    InputValidators.validateNotEmpty(mealId, "mealId");

    String id = systemId + "_" + mealId;
    MealEntity meal = mealRepository.findById(id)
        .orElseThrow(() -> new InsuScanNotFoundException("Meal not found: " + mealId));

    // Get user profile for calculation params
    UserEntity user = userRepository.findById(meal.getUserId())
        .orElseThrow(() -> new InsuScanNotFoundException("User not found for meal"));

    // Store user context at meal time
    meal.setCurrentGlucose(currentGlucose);
    meal.setActivityLevel(activityLevel);
    meal.setWasSickMode(sickMode != null && sickMode);
    meal.setWasStressMode(stressMode != null && stressMode);

    // Calculate dose using full calculator
    Float totalCarbs = meal.getTotalCarbs();
    if (totalCarbs != null && totalCarbs > 0) {
        
        // Get adjustment percentages (only if mode is active)
        Integer sickPercent = (sickMode != null && sickMode) 
            ? user.getSickDayAdjustment() : 0;
        Integer stressPercent = (stressMode != null && stressMode) 
            ? user.getStressAdjustment() : 0;

        InsulinCalculator calculator = new InsulinCalculator();
        CalculationParams params = new CalculationParams.Builder()
            .withTotalCarbs(totalCarbs)
            .withInsulinCarbRatio(user.getInsulinCarbRatio())  // uses new Float overload
            .withCorrectionFactor(user.getCorrectionFactor())
            .withTargetGlucose(user.getTargetGlucose())
            .withCurrentGlucose(currentGlucose)
            .withActivityLevel(activityLevel)
            .withSickDayPercent(sickPercent)
            .withStressPercent(stressPercent)
            .withLightExercisePercent(user.getLightExerciseAdjustment())
            .withIntenseExercisePercent(user.getIntenseExerciseAdjustment())
            .build();

        CalculationResult result = calculator.calculate(params);

        // Store full breakdown for history display
        meal.setCarbDose(result.getCarbDose());
        meal.setCorrectionDose(result.getCorrectionDose());
        meal.setSickAdjustment(result.getSickAdjustment());
        meal.setStressAdjustment(result.getStressAdjustment());
        meal.setExerciseAdjustment(result.getExerciseAdjustment());
        meal.setRecommendedDose(result.getRoundedDose());
        meal.setProfileComplete(result.isProfileComplete());
        
        // Warning message if profile incomplete
        if (!result.isProfileComplete()) {
            meal.setInsulinMessage("Profile incomplete - using default values");
        }
    }

    // Set actual dose (user override or recommended)
    if (actualDose != null) {
        if (!InputValidators.isValidInsulinDose(actualDose)) {
            throw new InsuScanInvalidInputException(
                "Invalid insulin dose: " + actualDose + ". Must be between 0 and 100 units.");
        }
        meal.setActualDose(actualDose);
    } else if (meal.getRecommendedDose() != null) {
        meal.setActualDose(meal.getRecommendedDose());
    }

    // Update status and timestamp
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

        // Must be confirmed first
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

        // Update portion analysis fields
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

        String userId = systemId + "_" + email;

        return mealRepository.findRecentByUserId(userId, count)
            .stream()
            .map(mealConverter::toBoundary)
            .collect(Collectors.toList());
    }

    @Override
    public long getMealCountForUser(String systemId, String email) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String userId = systemId + "_" + email;
        return mealRepository.countByUserId(userId);
    }


    // Verify admin access
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
    
    @Override
    public List<MealBoundary> getMealsByDateRange(String systemId, String email,
                                                   LocalDate from, LocalDate to,
                                                   int page, int size) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);
        
        String userId = systemId + "_" + email;
        
        // Convert dates to timestamps
        Date fromDate = Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date toDate = Date.from(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        return mealRepository.findByUserIdAndDateRange(userId, fromDate, toDate, page, size)
            .stream()
            .map(mealConverter::toBoundary)
            .collect(Collectors.toList());
    }
}
