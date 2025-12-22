package com.insuscan.service;

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
import com.insuscan.util.InputValidators;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserConverter userConverter;

    @Value("${spring.application.name}")
    private String systemId;

    public UserServiceImpl(UserRepository userRepository, UserConverter userConverter) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
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

        // Update medical profile if provided
        if (update.getInsulinCarbRatio() != null) {
            Float ratio = InputValidators.parseInsulinRatio(update.getInsulinCarbRatio());
            existing.setInsulinCarbRatio(ratio);
        }
        if (update.getCorrectionFactor() != null) {
            existing.setCorrectionFactor(update.getCorrectionFactor());
        }
        if (update.getTargetGlucose() != null) {
            existing.setTargetGlucose(update.getTargetGlucose());
        }

        // Update syringe settings if provided
        if (update.getSyringeType() != null) {
            existing.setSyringeType(update.getSyringeType());
        }
        if (update.getCustomSyringeLength() != null) {
            existing.setCustomSyringeLength(update.getCustomSyringeLength());
        }

        existing.setUpdatedAt(new Date());
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
        return userRepository.findById(id)
            .map(userConverter::toBoundary);
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
