package com.insuscan.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.service.UserService;

@RestController
@RequestMapping(path = "/users")
@CrossOrigin(origins = "*")
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
    @GetMapping(
        path = "/{systemId}/{email}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserBoundary getUser(
            @PathVariable String systemId,
            @PathVariable String email) {
        return userService.getUserById(systemId, email)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + email));
    }
}
