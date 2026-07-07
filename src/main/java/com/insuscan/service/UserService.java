package com.insuscan.service;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;

import java.util.List;
import java.util.Optional;

/**
 * User management service: registration, login, profile updates, admin queries,
 * and medical profile checks used by the insulin flow.
 */
public interface UserService {

    UserBoundary createUser(NewUserBoundary newUser);

    Optional<UserBoundary> login(String systemId, String email);

    UserBoundary updateUser(String systemId, String email, UserBoundary update);

    List<UserBoundary> getAllUsers(String adminSystemId, String adminEmail, int page, int size);

    void deleteAllUsers(String adminSystemId, String adminEmail);

    Optional<UserBoundary> getUserById(String systemId, String email);

    boolean hasCompleteMedicalProfile(String systemId, String email);

    void deleteUser(String adminSystemId, String adminEmail, String targetSystemId, String targetEmail);
}