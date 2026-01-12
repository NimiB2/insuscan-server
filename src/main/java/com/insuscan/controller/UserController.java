package com.insuscan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.service.UserService;

@RestController
@RequestMapping(path = "/users")
@CrossOrigin(origins = "*")
@Tag(name = "Users", description = "User management and profile endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // POST /users - Create new user (registration)
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary createUser(@RequestBody NewUserBoundary newUser) {
        return userService.createUser(newUser);
    }

    // GET /users/login/{systemId}/{email} - Login
    @GetMapping(
        path = "/login/{systemId}/{email}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary login(
            @PathVariable String systemId,
            @PathVariable String email) {
        return userService.login(systemId, email)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + email));
    }

    // PUT /users/{systemId}/{email} - Update user profile
    @PutMapping(
        path = "/{systemId}/{email}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary updateUser(
            @PathVariable String systemId,
            @PathVariable String email,
            @RequestBody UserBoundary update) {
        return userService.updateUser(systemId, email, update);
    }

    // GET /users/{systemId}/{email} - Get user by ID
    // Note: Email in path variable - Spring automatically URL decodes it
    @GetMapping(
        path = "/{systemId}/{email:.+}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary getUser(
            @PathVariable String systemId,
            @PathVariable String email) {
        // Log for debugging
        System.out.println("Looking up user - systemId: " + systemId + ", email: " + email);
        String constructedId = systemId + "_" + email;
        System.out.println("Constructed user ID: " + constructedId);
        
        return userService.getUserById(systemId, email)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + email + " (ID: " + constructedId + ")"));
    }
    
    // Alternative endpoint using query parameter (more reliable for emails with special chars)
    @GetMapping(
        path = "/by-email",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary getUserByEmail(
            @RequestParam(value = "systemId", defaultValue = "insuscan") String systemId,
            @RequestParam(value = "email") String email) {
        return userService.getUserById(systemId, email)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + email));
    }

    /**
     * Quick user creation endpoint for prototype/testing
     * POST /users/quick?email=user@example.com&userName=John&insulinRatio=1:10
     * 
     * Creates a PATIENT user with minimal required fields and sensible defaults
     */
    @Operation(summary = "Quick user creation", description = "Creates a PATIENT user with minimal required fields. Useful for prototype/testing.")
    @PostMapping(
        path = "/quick",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary createQuickUser(
            @Parameter(description = "User email address (required)", required = true, example = "test@example.com")
            @RequestParam(value = "email") String email,
            
            @Parameter(description = "User's display name (optional, defaults to email prefix)", example = "John Doe")
            @RequestParam(value = "userName", required = false) String userName,
            
            @Parameter(description = "Insulin to carb ratio in format '1:10' (optional, default: 1:10)", example = "1:10")
            @RequestParam(value = "insulinRatio", required = false, defaultValue = "1:10") String insulinRatio,
            
            @Parameter(description = "Correction factor in mg/dL per unit (optional, default: 50)", example = "50")
            @RequestParam(value = "correctionFactor", required = false) Float correctionFactor,
            
            @Parameter(description = "Target blood glucose in mg/dL (optional, default: 100)", example = "100")
            @RequestParam(value = "targetGlucose", required = false) Integer targetGlucose) {
        
        NewUserBoundary newUser = new NewUserBoundary();
        newUser.setEmail(email);
        newUser.setRole(com.insuscan.enums.UserRole.PATIENT);
        newUser.setUserName(userName != null ? userName : email.split("@")[0]);
        newUser.setInsulinCarbRatio(insulinRatio);
        
        // Set defaults if not provided
        if (correctionFactor == null) {
            correctionFactor = 50f; // Default: 50 mg/dL per unit
        }
        if (targetGlucose == null) {
            targetGlucose = 100; // Default: 100 mg/dL
        }
        
        newUser.setCorrectionFactor(correctionFactor);
        newUser.setTargetGlucose(targetGlucose);
        
        return userService.createUser(newUser);
    }

    /**
     * Check if user has complete medical profile
     * GET /users/{systemId}/{email}/profile-complete
     */
    @GetMapping(
        path = "/{systemId}/{email}/profile-complete",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Boolean> checkProfileComplete(
            @PathVariable String systemId,
            @PathVariable String email) {
        boolean complete = userService.hasCompleteMedicalProfile(systemId, email);
        return ResponseEntity.ok(complete);
    }
}
