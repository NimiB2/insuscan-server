package com.insuscan.service;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;

import java.util.List;
import java.util.Optional;

// User management service interface
public interface UserService {

    // Create new user (registration)
    UserBoundary createUser(NewUserBoundary newUser);

    // Login - returns user if credentials match
    Optional<UserBoundary> login(String systemId, String email);

    // Update user profile
    UserBoundary updateUser(String systemId, String email, UserBoundary update);

    // Get all users (admin only, with pagination)
    List<UserBoundary> getAllUsers(String adminSystemId, String adminEmail, int page, int size);

    // Delete all users (admin only)
    void deleteAllUsers(String adminSystemId, String adminEmail);

    // Get user by ID
    Optional<UserBoundary> getUserById(String systemId, String email);

    // Check if user exists
    boolean userExists(String systemId, String email);
    
    // Check if user has complete medical profile (for insulin calculation)
    boolean hasCompleteMedicalProfile(String systemId, String email);

    // Delete single user (admin only)
    void deleteUser(String adminSystemId, String adminEmail, String targetSystemId, String targetEmail);
}
