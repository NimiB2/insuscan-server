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
            @RequestParam("adminSystemId") String adminSystemId,
            @RequestParam("adminEmail") String adminEmail,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return userService.getAllUsers(adminSystemId, adminEmail, page, size);
    }

    // DELETE /admin/users - Delete all users
    @DeleteMapping(path = "/users")
    public void deleteAllUsers(
            @RequestParam("adminSystemId") String adminSystemId,
            @RequestParam("adminEmail") String adminEmail) {
        userService.deleteAllUsers(adminSystemId, adminEmail);
    }

    // DELETE /admin/users/{systemId}/{email} - Delete single user
    @DeleteMapping(path = "/users/{systemId}/{email:.+}")
    public void deleteUser(
            @RequestParam("adminSystemId") String adminSystemId,
            @RequestParam("adminEmail") String adminEmail,
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email) {
        userService.deleteUser(adminSystemId, adminEmail, systemId, email);
    }

    // DELETE /admin/meals - Delete all meals
    @DeleteMapping(path = "/meals")
    public void deleteAllMeals(
            @RequestParam("adminSystemId") String adminSystemId,
            @RequestParam("adminEmail") String adminEmail) {
        mealService.deleteAllMeals(adminSystemId, adminEmail);
    }

    // DELETE /admin/meals/user/{systemId}/{email} - Delete all meals for a user
    @DeleteMapping(path = "/meals/user/{systemId}/{email:.+}")
    public void deleteAllMealsForUser(
            @RequestParam("adminSystemId") String adminSystemId,
            @RequestParam("adminEmail") String adminEmail,
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email) {
        mealService.deleteAllMealsForUser(adminSystemId, adminEmail, systemId, email);
    }
}
