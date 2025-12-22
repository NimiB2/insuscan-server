package com.insuscan.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.insuscan.boundary.UserBoundary;
import com.insuscan.service.MealService;
import com.insuscan.service.UserService;

import java.util.List;

@RestController
@RequestMapping(path = "/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final UserService userService;
    private final MealService mealService;

    public AdminController(UserService userService, MealService mealService) {
        this.userService = userService;
        this.mealService = mealService;
    }

    // GET /admin/users - Get all users (paginated)
    @GetMapping(
        path = "/users",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UserBoundary> getAllUsers(
            @RequestParam String adminSystemId,
            @RequestParam String adminEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.getAllUsers(adminSystemId, adminEmail, page, size);
    }

    // DELETE /admin/users - Delete all users
    @DeleteMapping(path = "/users")
    public void deleteAllUsers(
            @RequestParam String adminSystemId,
            @RequestParam String adminEmail) {
        userService.deleteAllUsers(adminSystemId, adminEmail);
    }

    // DELETE /admin/users/{systemId}/{email} - Delete single user
    @DeleteMapping(path = "/users/{systemId}/{email}")
    public void deleteUser(
            @RequestParam String adminSystemId,
            @RequestParam String adminEmail,
            @PathVariable String systemId,
            @PathVariable String email) {
        userService.deleteUser(adminSystemId, adminEmail, systemId, email);
    }

    // DELETE /admin/meals - Delete all meals
    @DeleteMapping(path = "/meals")
    public void deleteAllMeals(
            @RequestParam String adminSystemId,
            @RequestParam String adminEmail) {
        mealService.deleteAllMeals(adminSystemId, adminEmail);
    }

    // DELETE /admin/meals/user/{systemId}/{email} - Delete all meals for a user
    @DeleteMapping(path = "/meals/user/{systemId}/{email}")
    public void deleteAllMealsForUser(
            @RequestParam String adminSystemId,
            @RequestParam String adminEmail,
            @PathVariable String systemId,
            @PathVariable String email) {
        mealService.deleteAllMealsForUser(adminSystemId, adminEmail, systemId, email);
    }
}
