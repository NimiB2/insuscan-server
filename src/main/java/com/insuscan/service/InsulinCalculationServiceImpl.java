package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.UserEntity;
import com.insuscan.util.ApiLogger;
import com.insuscan.calculation.InsulinCalculator;
import com.insuscan.calculation.CalculationParams;
import com.insuscan.calculation.CalculationResult;
import com.insuscan.calculation.InsulinDefaults;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service implementation for Insulin Dose Calculation.
 * <p>
 * This service acts as an orchestrator/facade. It: 1. Validates inputs 2. Loads
 * User Profile data 3. Builds a context object (CalculationParams) 4. Delegates
 * the medical math to {@link InsulinCalculator} ("The Golden Logic") 5. Returns
 * the structured result
 */
@Service
public class InsulinCalculationServiceImpl implements InsulinCalculationService {

	private static final Logger log = LoggerFactory.getLogger(InsulinCalculationServiceImpl.class);

	private final UserRepository userRepository;
	private final ApiLogger apiLogger;

	@Value("${spring.application.name}")
	private String systemId;

	public InsulinCalculationServiceImpl(UserRepository userRepository, ApiLogger apiLogger) {
		this.userRepository = userRepository;
		this.apiLogger = apiLogger;
	}

	@Override
	public InsulinCalculationBoundary calculateDose(Float totalCarbs, Integer currentGlucose, UserIdBoundary userId) {
		// Call full method with no adjustments
		return calculateDoseWithAdjustments(totalCarbs, currentGlucose, "normal", false, false, userId);
	}

	@Override
	public InsulinCalculationBoundary calculateDoseWithAdjustments(Float totalCarbs, Integer currentGlucose,
			String activityLevel, Boolean sickModeEnabled, Boolean stressModeEnabled, UserIdBoundary userId) {

		long startTime = System.currentTimeMillis();

		// 1. Validate inputs
		if (totalCarbs == null || totalCarbs < 0) {
			String msg = "Total carbs must be provided and >= 0";
			apiLogger.insulinCalcError(msg);
			throw new IllegalArgumentException(msg);
		}

		// 2. Log start
		String userEmail = userId != null ? userId.getEmail() : null;
		apiLogger.insulinCalcStart(totalCarbs, currentGlucose, activityLevel, sickModeEnabled, stressModeEnabled,
				userEmail);

		// 3. Load user profile
		UserEntity user = loadUserProfile(userId);

		// 4. Build Calculation Params (Context)
		// This merges User Profile settings + Request specific data (Carbs, Glucose,
		// Activity)
		CalculationParams.Builder builder = new CalculationParams.Builder().fromUser(user).withTotalCarbs(totalCarbs)
				.withCurrentGlucose(currentGlucose).withActivityLevel(activityLevel);


		CalculationParams params = builder.build();

		// 5. Execute Calculation (Delegated to Domain Logic)
		InsulinCalculator calculator = new InsulinCalculator();
		CalculationResult result = calculator.calculate(params);

		// 6. Log Complete Breakdown
		apiLogger.insulinCalcBreakdown(result.getCarbDose(), result.getCorrectionDose(),
		        result.getBaseDose(), result.getTotalDose(), result.getRoundedDose());

		if (result.getWarning() != null && !result.getWarning().isEmpty()) {
			apiLogger.insulinCalcWarning(result.getWarning());
		}

		long totalTime = System.currentTimeMillis() - startTime;
		apiLogger.insulinCalcComplete(result.getTotalDose(), result.getRoundedDose(), totalTime);

		// 7. Map to Response Boundary
		return mapToBoundary(params, result);
	}

	private InsulinCalculationBoundary mapToBoundary(CalculationParams params, CalculationResult result) {
		InsulinCalculationBoundary response = new InsulinCalculationBoundary();

		// Inputs
		response.setTotalCarbs(params.getTotalCarbs());
		response.setCurrentGlucose(params.getCurrentGlucose() != null ? params.getCurrentGlucose().floatValue() : null);

		// Profile Used
		String ratioDisplay = params.getInsulinCarbRatio() != null
				? String.format("1:%.0f", params.getInsulinCarbRatio())
				: "Default";
		response.setInsulinCarbRatioUsed(ratioDisplay);
		response.setCorrectionFactorUsed(params.getCorrectionFactor());
		response.setTargetGlucoseUsed(params.getTargetGlucose());

		// Results
		response.setCarbDose(result.getCarbDose());
		response.setCorrectionDose(result.getCorrectionDose());

		// Final
		response.setTotalRecommendedDose(result.getRoundedDose());
		response.setWarning(result.getWarning());

		return response;
	}

	private UserEntity loadUserProfile(UserIdBoundary userId) {
		if (userId == null || userId.getEmail() == null) {
			return null;
		}
		try {
			String userDocId = (userId.getSystemId() != null ? userId.getSystemId() : systemId) + "_"
					+ userId.getEmail();
			return userRepository.findById(userDocId).orElse(null);
		} catch (Exception e) {
			log.warn("Failed to load user profile: {}", e.getMessage());
			return null;
		}
	}
}
