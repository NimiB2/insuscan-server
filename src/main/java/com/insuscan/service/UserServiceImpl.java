package com.insuscan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;
import com.insuscan.converter.UserConverter;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.UserRole;
import com.insuscan.exception.InsuScanInvalidInputException;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.exception.InsuScanUnauthorizedException;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.InputValidators;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    
    private final UserRepository userRepository;
    private final UserConverter userConverter;
    private final ApiLogger apiLogger;

    @Value("${spring.application.name}")
    private String systemId;

    public UserServiceImpl(UserRepository userRepository, UserConverter userConverter,ApiLogger apiLogger) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.apiLogger = apiLogger;
    }

    @Override
    public UserBoundary createUser(NewUserBoundary newUser) {
        // Validate input
        validateNewUser(newUser);

        // Convert to boundary then entity
        UserBoundary boundary = userConverter.newUserToBoundary(newUser);
        UserEntity entity = userConverter.toEntity(boundary);

        // Check if user already exists
        if (userRepository.existsById(entity.getId())) {
            throw new InsuScanInvalidInputException(
                "User already exists with email: " + newUser.getEmail());
        }

        // Set timestamps
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());

        // Save and return
        UserEntity saved = userRepository.save(entity);
        return userConverter.toBoundary(saved);
    }

    @Override
    public Optional<UserBoundary> login(String systemId, String email) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String id = systemId + "_" + email;
        return userRepository.findById(id)
            .map(userConverter::toBoundary);
    }

    @Override
    public UserBoundary updateUser(String systemId, String email, UserBoundary update) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String id = systemId + "_" + email;
        UserEntity existing = userRepository.findById(id)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + email));

        // Update fields if provided
        if (update.getUserName() != null) {
            existing.setUserName(update.getUserName());
        }
        if (update.getAvatar() != null) {
            existing.setAvatar(update.getAvatar());
        }
        if (update.getRole() != null) {
            existing.setRole(update.getRole());
        }

        // Update medical profile if provided (with validation)
        if (update.getInsulinCarbRatio() != null) {
            if (!InputValidators.isValidInsulinRatio(update.getInsulinCarbRatio())) {
                throw new InsuScanInvalidInputException(
                    "Invalid insulin:carb ratio format. Use format like '1:10'");
            }
            Float ratio = InputValidators.parseInsulinRatio(update.getInsulinCarbRatio());
            existing.setInsulinCarbRatio(ratio);
        }
        if (update.getCorrectionFactor() != null) {
            if (!InputValidators.isValidCorrectionFactor(update.getCorrectionFactor())) {
                throw new InsuScanInvalidInputException(
                    "Invalid correction factor. Must be between 1 and 200 mg/dL per unit");
            }
            existing.setCorrectionFactor(update.getCorrectionFactor());
        }
        if (update.getTargetGlucose() != null) {
            if (!InputValidators.isValidTargetGlucose(update.getTargetGlucose())) {
                throw new InsuScanInvalidInputException(
                    "Invalid target glucose. Must be between 60 and 200 mg/dL");
            }
            existing.setTargetGlucose(update.getTargetGlucose());
        }

        // Update syringe settings if provided
        if (update.getSyringeType() != null) {
            existing.setSyringeType(update.getSyringeType());
        }
        if (update.getCustomSyringeLength() != null) {
            existing.setCustomSyringeLength(update.getCustomSyringeLength());
        }
        
     // Personal info
        if (update.getAge() != null) {
            existing.setAge(update.getAge());
        }
        if (update.getGender() != null) {
            existing.setGender(update.getGender());
        }
        if (update.getPregnant() != null) {
            existing.setPregnant(update.getPregnant());
        }
        if (update.getDueDate() != null) {
            existing.setDueDate(update.getDueDate());
        }

        // Medical info
        if (update.getDiabetesType() != null) {
            existing.setDiabetesType(update.getDiabetesType());
        }
        if (update.getInsulinType() != null) {
            existing.setInsulinType(update.getInsulinType());
        }
        if (update.getActiveInsulinTime() != null) {
            existing.setActiveInsulinTime(update.getActiveInsulinTime());
        }

        // Dose settings
        if (update.getDoseRounding() != null) {
            existing.setDoseRounding(update.getDoseRounding());
        }

        // Adjustment factors
        if (update.getSickDayAdjustment() != null) {
            existing.setSickDayAdjustment(update.getSickDayAdjustment());
        }
        if (update.getStressAdjustment() != null) {
            existing.setStressAdjustment(update.getStressAdjustment());
        }
        if (update.getLightExerciseAdjustment() != null) {
            existing.setLightExerciseAdjustment(update.getLightExerciseAdjustment());
        }
        if (update.getIntenseExerciseAdjustment() != null) {
            existing.setIntenseExerciseAdjustment(update.getIntenseExerciseAdjustment());
        }

        // Preferences
        if (update.getGlucoseUnits() != null) {
            existing.setGlucoseUnits(update.getGlucoseUnits());
        }

        existing.setUpdatedAt(new Date());
        apiLogger.logUserEntityBeforeSave(existing);
        UserEntity saved = userRepository.save(existing);
        return userConverter.toBoundary(saved);
    }

    @Override
    public List<UserBoundary> getAllUsers(String adminSystemId, String adminEmail, int page, int size) {
        // Verify admin access
        verifyAdminAccess(adminSystemId, adminEmail);

        return userRepository.findAll(page, size)
            .stream()
            .map(userConverter::toBoundary)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteAllUsers(String adminSystemId, String adminEmail) {
        // Verify admin access
        verifyAdminAccess(adminSystemId, adminEmail);

        userRepository.deleteAll();
    }

    @Override
    public Optional<UserBoundary> getUserById(String systemId, String email) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String id = systemId + "_" + email;
        log.debug("Looking up user - systemId: {}, email: {}, constructed ID: {}", systemId, email, id);
        
        Optional<UserEntity> user = userRepository.findById(id);
        if (user.isPresent()) {
            log.debug("User found: {}", id);
        } else {
            log.warn("User not found with ID: {}", id);
        }
        
        return user.map(userConverter::toBoundary);
    }

    @Override
    public boolean userExists(String systemId, String email) {
        String id = systemId + "_" + email;
        return userRepository.existsById(id);
    }

    @Override
    public void deleteUser(String adminSystemId, String adminEmail, 
                          String targetSystemId, String targetEmail) {
        // Verify admin access
        verifyAdminAccess(adminSystemId, adminEmail);

        String targetId = targetSystemId + "_" + targetEmail;
        if (!userRepository.existsById(targetId)) {
            throw new InsuScanNotFoundException("User not found: " + targetEmail);
        }

        userRepository.deleteById(targetId);
    }

    @Override
    public boolean hasCompleteMedicalProfile(String systemId, String email) {
        String id = systemId + "_" + email;
        UserEntity user = userRepository.findById(id).orElse(null);
        
        if (user == null) {
            return false;
        }
        
        // Medical profile is complete if insulin:carb ratio is set
        // (correction factor and target glucose have defaults, so not strictly required)
        return user.getInsulinCarbRatio() != null;
    }

    // Validate new user registration data
    private void validateNewUser(NewUserBoundary newUser) {
        if (newUser == null) {
            throw new InsuScanInvalidInputException("User data cannot be null");
        }

        InputValidators.validateEmail(newUser.getEmail());

        if (newUser.getRole() == null) {
            throw new InsuScanInvalidInputException("User role is required");
        }

        // Validate optional insulin ratio format
        if (newUser.getInsulinCarbRatio() != null 
                && !InputValidators.isValidInsulinRatio(newUser.getInsulinCarbRatio())) {
            throw new InsuScanInvalidInputException(
                "Invalid insulin:carb ratio format. Use format like '1:10'");
        }

        // Validate medical profile if provided
        if (newUser.getCorrectionFactor() != null 
                && !InputValidators.isValidCorrectionFactor(newUser.getCorrectionFactor())) {
            throw new InsuScanInvalidInputException(
                "Invalid correction factor. Must be between 1 and 200 mg/dL per unit");
        }

        if (newUser.getTargetGlucose() != null 
                && !InputValidators.isValidTargetGlucose(newUser.getTargetGlucose())) {
            throw new InsuScanInvalidInputException(
                "Invalid target glucose. Must be between 60 and 200 mg/dL");
        }
    }

    // Verify the requesting user has admin privileges
    private void verifyAdminAccess(String systemId, String email) {
        InputValidators.validateSystemId(systemId);
        InputValidators.validateEmail(email);

        String id = systemId + "_" + email;
        UserEntity admin = userRepository.findById(id)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "Admin user not found: " + email));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new InsuScanUnauthorizedException(
                "User does not have admin privileges: " + email);
        }
    }
}
