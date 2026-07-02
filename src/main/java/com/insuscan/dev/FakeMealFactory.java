package com.insuscan.dev;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.MealIdBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.enums.MealStatus;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only factory that produces fake meal history for a given user,
 * used to seed demo data without invoking the real scan pipeline.
 */
public class FakeMealFactory {

    private final String systemId;
    private final String userEmail;

    public FakeMealFactory(String systemId, String userEmail) {
        this.systemId = systemId;
        this.userEmail = userEmail;
    }

    public List<MealBoundary> buildFakeHistory() {
        return List.of(
            build("Grilled Chicken & Rice", daysAgo(1, 12), 52f, 4.5f, 108, List.of(
                foodItem("Grilled Chicken", "עוף צלוי", 200f, 0f, 0.95f),
                foodItem("White Rice", "אורז לבן", 150f, 52f, 0.90f)
            )),
            build("Oatmeal & Banana", daysAgo(1, 7), 65f, 5.2f, 95, List.of(
                foodItem("Oatmeal", "שיבולת שועל", 100f, 45f, 0.93f),
                foodItem("Banana", "בננה", 120f, 20f, 0.97f)
            )),
            build("Pasta Bolognese", daysAgo(2, 19), 78f, 6.2f, 130, List.of(
                foodItem("Pasta", "פסטה", 250f, 60f, 0.92f),
                foodItem("Meat Sauce", "רוטב בולונז", 120f, 18f, 0.88f)
            )),
            build("Shakshuka", daysAgo(3, 9), 22f, 2.0f, 100, List.of(
                foodItem("Eggs", "ביצים", 160f, 2f, 0.96f),
                foodItem("Tomato Sauce", "רוטב עגבניות", 200f, 20f, 0.89f)
            )),
            build("Tuna Salad", daysAgo(3, 13), 15f, 1.2f, 92, List.of(
                foodItem("Tuna", "טונה", 150f, 0f, 0.94f),
                foodItem("Vegetables", "ירקות", 100f, 10f, 0.91f),
                foodItem("Whole Wheat Bread", "לחם מלא", 60f, 14f, 0.90f)
            )),
            build("Rice & Lentils", daysAgo(4, 20), 70f, 5.6f, 115, List.of(
                foodItem("Rice", "אורז", 180f, 50f, 0.93f),
                foodItem("Lentils", "עדשים", 150f, 20f, 0.91f)
            )),
            build("Pizza Margherita", daysAgo(5, 19), 88f, 7.0f, 140, List.of(
                foodItem("Pizza Dough", "בצק פיצה", 300f, 70f, 0.91f),
                foodItem("Mozzarella", "מוצרלה", 80f, 1f, 0.90f),
                foodItem("Tomato Base", "רוטב עגבניות", 60f, 17f, 0.88f)
            )),
            build("Greek Salad & Pita", daysAgo(5, 13), 38f, 3.0f, 105, List.of(
                foodItem("Pita Bread", "פיתה", 80f, 30f, 0.92f),
                foodItem("Feta Cheese", "גבינה פטה", 60f, 1f, 0.95f),
                foodItem("Vegetables", "ירקות", 150f, 7f, 0.93f)
            )),
            build("Pancakes", daysAgo(6, 8), 72f, 5.8f, 118, List.of(
                foodItem("Pancakes", "פנקייקס", 200f, 60f, 0.90f),
                foodItem("Maple Syrup", "סירופ מייפל", 30f, 12f, 0.95f)
            )),
            build("Beef Stew", daysAgo(7, 18), 40f, 3.2f, 125, List.of(
                foodItem("Beef", "בשר בקר", 200f, 0f, 0.93f),
                foodItem("Potatoes", "תפוחי אדמה", 150f, 30f, 0.91f),
                foodItem("Carrots", "גזר", 80f, 10f, 0.92f)
            )),
            build("Corn Flakes & Milk", daysAgo(8, 7), 55f, 4.4f, 98, List.of(
                foodItem("Corn Flakes", "קורנפלקס", 60f, 48f, 0.92f),
                foodItem("Milk", "חלב", 200f, 10f, 0.96f)
            )),
            build("Falafel & Hummus", daysAgo(9, 14), 62f, 5.0f, 112, List.of(
                foodItem("Falafel", "פלאפל", 150f, 30f, 0.90f),
                foodItem("Hummus", "חומוס", 100f, 15f, 0.92f),
                foodItem("Pita", "פיתה", 80f, 17f, 0.91f)
            )),
            build("Spaghetti Carbonara", daysAgo(10, 20), 82f, 6.5f, 135, List.of(
                foodItem("Spaghetti", "ספגטי", 250f, 70f, 0.92f),
                foodItem("Cream Sauce", "רוטב שמנת", 80f, 5f, 0.88f),
                foodItem("Parmesan", "פרמזן", 30f, 1f, 0.95f)
            )),
            build("Salmon & Sweet Potato", daysAgo(11, 13), 35f, 2.8f, 88, List.of(
                foodItem("Salmon", "סלמון", 200f, 0f, 0.96f),
                foodItem("Sweet Potato", "בטטה", 180f, 35f, 0.93f)
            )),
            build("Lentil Soup", daysAgo(12, 12), 45f, 3.6f, 107, List.of(
                foodItem("Red Lentils", "עדשים אדומות", 200f, 35f, 0.93f),
                foodItem("Bread Roll", "לחמנייה", 60f, 10f, 0.90f)
            )),
            build("Toast & Avocado", daysAgo(13, 8), 30f, 2.4f, 96, List.of(
                foodItem("Whole Wheat Toast", "טוסט מלא", 80f, 24f, 0.93f),
                foodItem("Avocado", "אבוקדו", 100f, 6f, 0.95f)
            )),
            build("Schnitzel & Fries", daysAgo(14, 19), 60f, 4.8f, 120, List.of(
                foodItem("Chicken Schnitzel", "שניצל עוף", 200f, 20f, 0.91f),
                foodItem("French Fries", "צ'יפס", 150f, 40f, 0.89f)
            )),
            build("Vegetable Stir Fry", daysAgo(15, 12), 48f, 3.8f, 90, List.of(
                foodItem("Mixed Vegetables", "ירקות מוקפצים", 250f, 20f, 0.92f),
                foodItem("Tofu", "טופו", 150f, 5f, 0.91f),
                foodItem("Rice", "אורז", 120f, 23f, 0.93f)
            )),
            build("Couscous Salad", daysAgo(17, 13), 55f, 4.4f, 103, List.of(
                foodItem("Couscous", "קוסקוס", 180f, 45f, 0.91f),
                foodItem("Roasted Vegetables", "ירקות צלויים", 120f, 10f, 0.93f)
            )),
            build("Cottage & Fruit Bowl", daysAgo(19, 8), 42f, 3.4f, 97, List.of(
                foodItem("Cottage Cheese", "קוטג'", 200f, 6f, 0.94f),
                foodItem("Strawberries", "תותים", 100f, 8f, 0.96f),
                foodItem("Granola", "גרנולה", 50f, 28f, 0.90f)
            ))
        );
    }

    private MealBoundary build(String title, Date scannedAt, float totalCarbs, float dose,
                               int glucose, List<FoodItemBoundary> items) {
        MealBoundary meal = new MealBoundary();

        MealIdBoundary mealId = new MealIdBoundary(UUID.randomUUID().toString(), systemId);
        meal.setMealId(mealId);

        UserIdBoundary userId = new UserIdBoundary(userEmail, systemId);
        meal.setUserId(userId);

        meal.setFoodItems(items);
        meal.setTotalCarbs(totalCarbs);
        meal.setRecommendedDose(dose);
        meal.setActualDose(dose);
        meal.setCarbDose(Math.round(dose * 0.85f * 10f) / 10f);
        meal.setCorrectionDose(Math.round(dose * 0.15f * 10f) / 10f);
        meal.setCurrentGlucose(glucose);
        meal.setAnalysisConfidence(0.90f);
        meal.setReferenceDetected(true);
        meal.setReferenceObjectType("CARD");
        meal.setContainerType("FLAT_PLATE");
        meal.setEstimatedWeight(totalCarbs * 4.5f);
        meal.setStatus(MealStatus.CONFIRMED);
        meal.setScannedAt(scannedAt);
        meal.setConfirmedAt(scannedAt);
        meal.setProfileComplete(true);
        meal.setNote(title);

        return meal;
    }

    private FoodItemBoundary foodItem(String name, String nameHe, float qty, float carbs, float confidence) {
        FoodItemBoundary item = new FoodItemBoundary();
        item.setName(name);
        item.setNameHebrew(nameHe);
        item.setQuantity(qty);
        item.setCarbs(carbs);
        item.setConfidence(confidence);
        return item;
    }

    private Date daysAgo(int days, int hour) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }

    private Date today(int hour) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }

    public List<MealBoundary> buildTodayMeals() {
        return List.of(
            build("Morning Yogurt & Granola", today(7), 38f, 3.0f, 94, List.of(
                foodItem("Greek Yogurt", "יוגורט יווני", 150f, 10f, 0.95f),
                foodItem("Granola", "גרנולה", 60f, 28f, 0.91f)
            )),
            build("Lunch - Rice & Vegetables", today(13), 62f, 5.0f, 112, List.of(
                foodItem("Brown Rice", "אורז מלא", 180f, 45f, 0.92f),
                foodItem("Grilled Vegetables", "ירקות צלויים", 150f, 17f, 0.93f)
            )),
            build("Afternoon Snack", today(16), 25f, 2.0f, 98, List.of(
                foodItem("Apple", "תפוח", 150f, 20f, 0.96f),
                foodItem("Peanut Butter", "חמאת בוטנים", 30f, 5f, 0.93f)
            ))
        );
    }
}