package com.insuscan.dev;

import com.insuscan.boundary.MealBoundary;
import com.insuscan.service.MealService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dev")
@CrossOrigin(origins = "*")
@Profile("dev")
public class DevController {

    private final MealService mealService;

    public DevController(MealService mealService) {
        this.mealService = mealService;
    }

    @PostMapping(path = "/seed/{systemId}/{email:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> seedFakeHistory(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email) {

        FakeMealFactory factory = new FakeMealFactory(systemId, email);

        return factory.buildFakeHistory().stream()
                .map(meal -> mealService.saveScannedMeal(systemId, email, meal))
                .collect(Collectors.toList());
    }
    
    
    @PostMapping(path = "/seed-today/{systemId}/{email:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> seedTodayMeals(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email) {

        FakeMealFactory factory = new FakeMealFactory(systemId, email);

        return factory.buildTodayMeals().stream()
                .map(meal -> mealService.saveScannedMeal(systemId, email, meal))
                .collect(Collectors.toList());
    }
}