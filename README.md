# InsuScan Server

Backend server for the InsuScan diabetes management application. Built with Spring Boot 3.5.0 and Firebase Firestore.

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.0**
- **Firebase Firestore** (database)
- **Firebase Admin SDK 9.2.0**
- **OpenAPI/Swagger** (API documentation)

## Prerequisites

- Java 21 or higher
- Gradle 8.x
- Firebase project with Firestore enabled
- Firebase service account credentials

## Firebase Setup

### 1. Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select existing one
3. Enable Firestore Database:
   - Go to **Build > Firestore Database**
   - Click **Create database**
   - Choose production or test mode
   - Select a location

### 2. Generate Service Account Key

1. In Firebase Console, go to **Project Settings > Service Accounts**
2. Click **Generate new private key**
3. Save the JSON file as `firebase-service-account.json`
4. Place it in `src/main/resources/` directory

### 3. Configure Application

Option A: Service account file (recommended for development)
```
src/main/resources/firebase-service-account.json
```

Option B: Environment variable (recommended for production)
```bash
export FIREBASE_PROJECT_ID=your-project-id
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```

## Running the Server

### Development

```bash
./gradlew bootRun
```

### Production

```bash
./gradlew build
java -jar build/libs/insuscan-1.0.0.jar
```

Server starts on **port 9693** by default.

## API Documentation

Once running, access Swagger UI at:
```
http://localhost:9693/swagger-ui.html
```

## API Endpoints

### Users

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/insuscan/users` | Create new user |
| GET | `/insuscan/users/{systemId}/{email}` | Get user by ID |
| PUT | `/insuscan/users/{systemId}/{email}` | Update user |
| DELETE | `/insuscan/users/{systemId}/{email}` | Delete user |
| GET | `/insuscan/users/login/{systemId}/{email}` | User login |

### Meals

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/insuscan/meals` | Create new meal (scan) |
| GET | `/insuscan/meals/{systemId}/{mealId}` | Get meal by ID |
| GET | `/insuscan/meals/user/{systemId}/{email}` | Get user's meals |
| PUT | `/insuscan/meals/{systemId}/{mealId}/food-items` | Update food items |
| PUT | `/insuscan/meals/{systemId}/{mealId}/confirm` | Confirm meal |
| PUT | `/insuscan/meals/{systemId}/{mealId}/complete` | Complete with insulin |
| DELETE | `/insuscan/meals/{systemId}/{mealId}` | Delete meal |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/insuscan/admin/users` | Get all users (paginated) |
| DELETE | `/insuscan/admin/users` | Delete all users |
| DELETE | `/insuscan/admin/meals` | Delete all meals |

## Data Models

### User

```json
{
  "userId": {
    "systemId": "insuscan",
    "email": "user@example.com"
  },
  "username": "John Doe",
  "role": "PATIENT",
  "insulinCarbRatio": "1:10",
  "correctionFactor": 50.0,
  "targetGlucose": 100,
  "syringeType": "STANDARD_1ML"
}
```

### Meal

```json
{
  "mealId": {
    "systemId": "insuscan",
    "id": "meal-uuid"
  },
  "userId": {
    "systemId": "insuscan",
    "email": "user@example.com"
  },
  "foodItems": [
    {
      "name": "Rice",
      "estimatedWeightGrams": 150.0,
      "carbsGrams": 45.0,
      "confidence": 0.85
    }
  ],
  "totalCarbs": 45.0,
  "status": "PENDING",
  "insulinCalculation": {
    "totalCarbs": 45.0,
    "carbDose": 4.5,
    "correctionDose": 0.0,
    "recommendedDose": 4.5,
    "insulinCarbRatio": "1:10"
  }
}
```

## Firestore Collections

- **users** - User profiles and settings
- **meals** - Meal records with food items and insulin calculations

### Document ID Format

Uses composite IDs: `{systemId}_{identifier}`

Examples:
- User: `insuscan_user@example.com`
- Meal: `insuscan_550e8400-e29b-41d4-a716-446655440000`

## Demo Data

On startup, the server creates demo data:
- 4 demo users (3 patients, 1 admin)
- 3 demo meals with food items

Demo admin credentials:
- Email: `admin@insuscan.com`
- System ID: `insuscan`

## Configuration

Key properties in `application.properties`:

```properties
# Server port
server.port=9693

# Firebase project ID
firebase.project.id=${FIREBASE_PROJECT_ID:insuscan-project}

# Service account file path
firebase.config.path=firebase-service-account.json

# External APIs (optional)
insuscan.google.vision.api.key=${GOOGLE_VISION_API_KEY:}
insuscan.usda.api.key=${USDA_API_KEY:}
```

## Troubleshooting

### Firebase Connection Issues

1. Verify service account JSON file exists and is valid
2. Check project ID matches your Firebase project
3. Ensure Firestore is enabled in Firebase Console
4. Check firewall/network allows Firebase connections

### Port Already in Use

Change port in `application.properties`:
```properties
server.port=9694
```

Or via command line:
```bash
java -jar build/libs/insuscan-1.0.0.jar --server.port=9694
```

## Project Structure

```
src/main/java/com/insuscan/
├── Application.java          # Main entry point
├── boundary/                 # API DTOs
├── config/                   # Firebase configuration
├── controller/               # REST controllers
├── converter/                # Entity <-> Boundary converters
├── crud/                     # Firestore repositories
├── data/                     # Entity classes
├── enums/                    # Enumerations
├── exception/                # Custom exceptions
├── init/                     # Data initializer
├── service/                  # Business logic
└── util/                     # Utilities (insulin calculator)
```

## License

Internal project - Afeka College of Engineering
