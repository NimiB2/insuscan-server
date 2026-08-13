package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.UserEntity;
import com.insuscan.util.ApiLogger;
import com.insuscan.calculation.InsulinCalculator;
import com.insuscan.calculation.CalculationParams;
import com.insuscan.calculation.CalculationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service implementation for Insulin Dose Calculation.
 * Orchestrates input validation, user profile loading, and delegates the medical
 * math to {@link InsulinCalculator}. Returns the structured result.
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
		return executeCalculation(totalCarbs, currentGlucose, userId, null, null, null);
	}

	@Override
	public InsulinCalculationBoundary calculateDose(Float totalCarbs, Integer currentGlucose, UserIdBoundary userId,
			Float planIcr, Float planIsf, Integer planTargetGlucose) {
		return executeCalculation(totalCarbs, currentGlucose, userId, planIcr, planIsf, planTargetGlucose);
	}

	private InsulinCalculationBoundary executeCalculation(Float totalCarbs, Integer currentGlucose,
			UserIdBoundary userId, Float planIcr, Float planIsf, Integer planTargetGlucose) {

		long startTime = System.currentTimeMillis();

		if (totalCarbs == null || totalCarbs < 0) {
			String msg = "Total carbs must be provided and >= 0";
			apiLogger.insulinCalcError(msg);
			throw new IllegalArgumentException(msg);
		}

		String userEmail = userId != null ? userId.getEmail() : null;
		apiLogger.insulinCalcStart(totalCarbs, currentGlucose, userEmail);

		UserEntity user = loadUserProfile(userId);

		CalculationParams.Builder builder = new CalculationParams.Builder().fromUser(user).withTotalCarbs(totalCarbs)
				.withCurrentGlucose(currentGlucose);

		applyPlanOverrides(builder, planIcr, planIsf, planTargetGlucose);

		CalculationParams params = builder.build();

		InsulinCalculator calculator = new InsulinCalculator();
		CalculationResult result = calculator.calculate(params);

		apiLogger.insulinCalcBreakdown(result.getCarbDose(), result.getCorrectionDose(),
				result.getBaseDose(), result.getTotalDose(), result.getRoundedDose());

		if (result.getWarning() != null && !result.getWarning().isEmpty()) {
			apiLogger.insulinCalcWarning(result.getWarning());
		}

		long totalTime = System.currentTimeMillis() - startTime;
		apiLogger.insulinCalcComplete(result.getTotalDose(), result.getRoundedDose(), totalTime);

		return mapToBoundary(params, result);
	}

	private void applyPlanOverrides(CalculationParams.Builder builder, Float planIcr, Float planIsf,
			Integer planTargetGlucose) {
		if (planIcr != null) {
			builder.withInsulinCarbRatio(planIcr);
		}
		if (planIsf != null) {
			builder.withCorrectionFactor(planIsf);
		}
		if (planTargetGlucose != null) {
			builder.withTargetGlucose(planTargetGlucose);
		}
	}

	private InsulinCalculationBoundary mapToBoundary(CalculationParams params, CalculationResult result) {
		InsulinCalculationBoundary response = new InsulinCalculationBoundary();

		response.setTotalCarbs(params.getTotalCarbs());
		response.setCurrentGlucose(params.getCurrentGlucose() != null ? params.getCurrentGlucose().floatValue() : null);

		String ratioDisplay = params.getInsulinCarbRatio() != null
				? String.format("1:%.0f", params.getInsulinCarbRatio())
				: "Default";
		response.setInsulinCarbRatioUsed(ratioDisplay);
		response.setCorrectionFactorUsed(params.getCorrectionFactor());
		response.setTargetGlucoseUsed(params.getTargetGlucose());

		response.setCarbDose(result.getCarbDose());
		response.setCorrectionDose(result.getCorrectionDose());
		response.setTotalRecommendedDose(result.getRoundedDose());
		response.setWarning(result.getWarning());

		response.setProfileComplete(result.isProfileComplete());
		response.setMissingFields(result.getMissingFields());
		response.setMessage(result.getWarning());

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