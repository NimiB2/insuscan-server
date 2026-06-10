package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.FoodRecognitionResult;
import com.insuscan.enums.ReferenceObjectDimensions;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.OpenAiJsonParser;
import com.insuscan.util.GeminiApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageAnalysisServiceImpl implements ImageAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(ImageAnalysisServiceImpl.class);

	// private final WebClient webClient;
	// private final ObjectMapper objectMapper;
	// private final ApiLogger apiLogger;
	// private final OpenAiJsonParser jsonParser;
	//
	// @Value("${openai.api.key:}")
	// private String openAiApiKey;
	//
	// @Value("${openai.model:gpt-4o-mini}")
	// private String openAiModel;
	//
	// public ImageAnalysisServiceImpl(WebClient.Builder webClientBuilder,
	// ObjectMapper objectMapper,
	// ApiLogger apiLogger,
	// OpenAiJsonParser jsonParser) {
	// this.webClient = webClientBuilder
	// .baseUrl("https://api.openai.com/v1")
	// .build();
	// this.objectMapper = objectMapper;
	// this.apiLogger = apiLogger;
	// this.jsonParser = jsonParser;
	// }
	private final GeminiApiClient geminiClient;
	private final ObjectMapper objectMapper;
	private final ApiLogger apiLogger;
	private final OpenAiJsonParser jsonParser;

	public ImageAnalysisServiceImpl(GeminiApiClient geminiClient, ObjectMapper objectMapper, ApiLogger apiLogger,
			OpenAiJsonParser jsonParser) {
		this.geminiClient = geminiClient;
		this.objectMapper = objectMapper;
		this.apiLogger = apiLogger;
		this.jsonParser = jsonParser;
	}

	@Override
	public FoodRecognitionResult analyzeImage(String base64Image, String referenceObjectType) {
		// // Log API key status
		// String keyPreview = (openAiApiKey != null && openAiApiKey.length() > 5)
		// ? openAiApiKey.substring(0, 5)
		// : "N/A";
		// apiLogger.apiKeyStatus("OPENAI", isServiceAvailable(), keyPreview);

		apiLogger.apiKeyStatus("GEMINI", isServiceAvailable(), geminiClient.getKeyPreview());

		if (!isServiceAvailable()) {
			apiLogger.openaiError("API key not configured", "ConfigurationException");
			return FoodRecognitionResult.failure("AI provider is not configured");
		}

		// Start request
		// apiLogger.openaiStart(openAiModel, base64Image.length());
		apiLogger.openaiStart(geminiClient.getVisionModel(), base64Image.length());

		long totalStartTime = System.currentTimeMillis();

		try {
			// First pass: strict prompt with reference type
			FoodRecognitionResult parsedResult = analyzeWithPrompt(base64Image, true, referenceObjectType);

			// Retry with relaxed prompt if needed
			if (parsedResult.getDetectedFoods().isEmpty()) {
				apiLogger.openaiRetry("Strict prompt returned 0 foods");
				parsedResult = analyzeWithPrompt(base64Image, false, referenceObjectType);
			}

			long totalTime = System.currentTimeMillis() - totalStartTime;

			if (parsedResult.getDetectedFoods().isEmpty()) {
				// v3: If we detected a container type (like REGULAR_BOWL), then an empty list
				// is
				// a valid "Empty Bowl" result, not a failure.
				if (parsedResult.getDetectedContainerType() != null
						&& !parsedResult.getDetectedContainerType().equals("FLAT_PLATE")) {
					log.info("[v3] Empty container detected. Treating as successful scan with 0 foods.");
				} else {
					apiLogger.openaiError("No foods detected even with relaxed prompt", "EmptyResult");
					return FoodRecognitionResult.failure("No foods detected in image. Try a clearer photo.");
				}
			}

			// Log parsed foods
			apiLogger.openaiParsedFoods(parsedResult.getDetectedFoods());
			apiLogger.openaiSuccess(parsedResult.getDetectedFoods().size(), totalTime);

			parsedResult.setSuccess(true);

			log.info("[v2] Container: type={}, fill={}%", parsedResult.getDetectedContainerType(),
					parsedResult.getContainerFillPercent());

			return parsedResult;

		} catch (RuntimeException e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
				return FoodRecognitionResult.failure("OpenAI rate limit exceeded. Please wait.");
			}
			return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
		} catch (Exception e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
		}
	}

	// @Override
	// public FoodRecognitionResult analyzeImageFromUrl(String imageUrl, String
	// referenceObjectType) {
	// apiLogger.apiKeyStatus("OPENAI", isServiceAvailable(),
	// openAiApiKey != null && openAiApiKey.length() > 5 ? openAiApiKey.substring(0,
	// 5) : "N/A");
	//
	// if (!isServiceAvailable()) {
	// return FoodRecognitionResult.failure("AI provider is not configured");
	// }
	//
	// apiLogger.openaiStart(openAiModel, 0);
	// long startTime = System.currentTimeMillis();
	//
	// try {
	// Map<String, Object> requestBody = buildOpenAiRequestWithUrl(imageUrl,
	// referenceObjectType);
	//
	// String response = webClient.post()
	// .uri("/chat/completions")
	// .header("Authorization", "Bearer " + openAiApiKey)
	// .header("Content-Type", "application/json")
	// .bodyValue(requestBody)
	// .retrieve()
	// .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
	// clientResponse -> clientResponse.bodyToMono(String.class)
	// .map(body -> new RuntimeException("OpenAI error: " + body)))
	// .bodyToMono(String.class)
	// .block();
	//
	// long elapsed = System.currentTimeMillis() - startTime;
	// apiLogger.openaiResponseReceived(elapsed, response != null ?
	// response.length() : 0);
	//
	// if (response == null || response.isBlank()) {
	// return FoodRecognitionResult.failure("Provider returned empty response");
	// }
	//
	// String content = extractContentFromResponse(response);
	// apiLogger.openaiRawResponse(content);
	//
	// if (content == null || content.isBlank()) {
	// return FoodRecognitionResult.failure("Could not extract content from
	// response");
	// }
	//
	// FoodRecognitionResult parsedResult = parseFoodsFromOpenAi(content);
	// apiLogger.openaiParsedFoods(parsedResult.getDetectedFoods());
	// parsedResult.setSuccess(true);
	//
	// return parsedResult;
	//
	// } catch (Exception e) {
	// apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
	// return FoodRecognitionResult.failure("Image analysis failed: " +
	// e.getMessage());
	// }
	// }

	@Override
	public FoodRecognitionResult analyzeImageFromUrl(String imageUrl, String referenceObjectType) {
		apiLogger.apiKeyStatus("GEMINI", isServiceAvailable(), geminiClient.getKeyPreview());

		if (!isServiceAvailable()) {
			return FoodRecognitionResult.failure("AI provider is not configured");
		}

		long startTime = System.currentTimeMillis();

		try {
			String prompt = buildPrompt(true, referenceObjectType) + "\n\nAnalyze the food in this image URL: "
					+ imageUrl;

			String model = geminiClient.getVisionModel();
			String content = geminiClient.callModel(model, prompt, null, null, 0.0);
			apiLogger.openaiRawResponse(content);

			long elapsed = System.currentTimeMillis() - startTime;
			apiLogger.openaiResponseReceived(elapsed, content != null ? content.length() : 0);

			if (content == null || content.isBlank()) {
				return FoodRecognitionResult.failure("Could not extract content from response");
			}

			FoodRecognitionResult parsedResult = parseFoodsFromOpenAi(content);
			apiLogger.openaiParsedFoods(parsedResult.getDetectedFoods());
			parsedResult.setSuccess(true);

			return parsedResult;

		} catch (Exception e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
		}
	}

	@Override
	public boolean isServiceAvailable() {
		// return openAiApiKey != null && !openAiApiKey.isBlank();
		return geminiClient.isServiceAvailable();
	}

	// private FoodRecognitionResult analyzeWithPrompt(String base64Image, boolean
	// strict,
	// String referenceObjectType)
	// throws Exception {
	// Map<String, Object> requestBody = buildOpenAiRequestWithBase64(base64Image,
	// strict, referenceObjectType);
	//
	// log.debug("[OPENAI] Sending {} prompt request...", strict ? "STRICT" :
	// "RELAXED");
	// long startTime = System.currentTimeMillis();
	//
	// String response = webClient.post()
	// .uri("/chat/completions")
	// .header("Authorization", "Bearer " + openAiApiKey)
	// .header("Content-Type", "application/json")
	// .bodyValue(requestBody)
	// .retrieve()
	// .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
	// clientResponse -> {
	// if (clientResponse.statusCode().value() == 429) {
	// return clientResponse.bodyToMono(String.class)
	// .map(body -> new RuntimeException("Rate limit exceeded: " + body));
	// }
	// return clientResponse.bodyToMono(String.class)
	// .map(body -> new RuntimeException("OpenAI API error: " + body));
	// })
	// .bodyToMono(String.class)
	// .block();
	//
	// long elapsed = System.currentTimeMillis() - startTime;
	// apiLogger.openaiResponseReceived(elapsed, response != null ?
	// response.length() : 0);
	//
	// if (response == null || response.isBlank()) {
	// throw new IllegalStateException("Provider returned empty response");
	// }
	//
	// String content = extractContentFromResponse(response);
	// apiLogger.openaiRawResponse(content);
	//
	// if (content == null || content.isBlank()) {
	// throw new IllegalStateException("Could not extract content from OpenAI
	// response");
	// }
	//
	// return parseFoodsFromOpenAi(content);
	// }

	private FoodRecognitionResult analyzeWithPrompt(String base64Image, boolean strict, String referenceObjectType)
			throws Exception {
		String prompt = buildPrompt(strict, referenceObjectType);

		log.debug("[GEMINI] Sending {} prompt request...", strict ? "STRICT" : "RELAXED");

		String model = geminiClient.getVisionModel();
		String content = geminiClient.callModel(model, prompt, base64Image, null, 0.0);
		apiLogger.openaiRawResponse(content);

		if (content == null || content.isBlank()) {
			throw new IllegalStateException("Gemini returned empty response");
		}

		return parseFoodsFromOpenAi(content);
	}

	// private Map<String, Object> buildOpenAiRequestWithBase64(String base64Image,
	// boolean strict,
	// String referenceObjectType) {
	// String prompt = buildPrompt(strict, referenceObjectType);
	//
	// Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
	// Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," +
	// base64Image);
	// Map<String, Object> imageContent = Map.of("type", "image_url", "image_url",
	// imageUrlObj);
	//
	// Map<String, Object> userMessage = Map.of(
	// "role", "user",
	// "content", List.of(textContent, imageContent));
	//
	// return Map.of(
	// "model", openAiModel,
	// "messages", List.of(userMessage),
	// "temperature", 0.0,
	// "max_tokens", 1200,
	// "response_format", Map.of("type", "json_object"));
	// }

	// private Map<String, Object> buildOpenAiRequestWithUrl(String imageUrl, String
	// referenceObjectType) {
	// String prompt = buildPrompt(true, referenceObjectType);
	//
	// Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
	// Map<String, Object> imageUrlObj = Map.of("url", imageUrl);
	// Map<String, Object> imageContent = Map.of("type", "image_url", "image_url",
	// imageUrlObj);
	//
	// Map<String, Object> userMessage = Map.of(
	// "role", "user",
	// "content", List.of(textContent, imageContent));
	//
	// return Map.of(
	// "model", openAiModel,
	// "messages", List.of(userMessage),
	// "temperature", 0.0,
	// "max_tokens", 1200,
	// "response_format", Map.of("type", "json_object"));
	// }

	private String buildPrompt(boolean strict, String referenceObjectType) {
		// Determine instructions based on selected reference type
		boolean isCard = referenceObjectType != null && (referenceObjectType.toLowerCase().contains("card")
				|| referenceObjectType.toLowerCase().contains("id"));

		// If strict is false (fallback mode), be more encouraging but still accurate
		if (!strict) {
			boolean isSyringe = referenceObjectType != null && referenceObjectType.toLowerCase().contains("syringe");
			String refInstructions = isCard
					? "SYSTEM ALERT: local detection found a " + ReferenceObjectDimensions.CARD.getScaleDescription()
							+ ". It is definitely there. Use these dimensions as your primary scale anchor."

					: (isSyringe
        ? "SYSTEM ALERT: local detection found a "
                + ReferenceObjectDimensions.INSULIN_PEN.getScaleDescription()
                + ". It is definitely there. Use these dimensions as your primary scale anchor."
        : "Identify the container size (diameter/depth) using standard plate sizes or nearby objects for scale.");

			return """
					You are a professional nutritionist's assistant AI.
					Analyze this image and identify ALL food items.

					Reference Object Context: %s

					INSTRUCTIONS:
					1. Identify the container (FLAT_PLATE, REGULAR_BOWL, DEEP_BOWL) and its fill level.
					2. Identify EVERY edible item in the container.
					3. The SYSTEM has already confirmed the presence of the container and scale object. Do not skip them.
					4. For each item, provide a visually descriptive name and its base ingredient.
					5. Use the SCALE ANCHOR to estimate grams accurately.
					6. CRITICAL: DO NOT return an empty "items" list unless the image is totally black or shows NO food-related context. If the image is blurry, provide your BEST GUESS (e.g., "Unidentified Dish", "Starch Group").

					Return ONLY this JSON structure:
					{
					   "container_type": "FLAT_PLATE" | "REGULAR_BOWL" | "DEEP_BOWL",
					   "container_fill_percent": <number>,
					   "items": [
					     {
					       "visual_name": "string",
					       "base_ingredient": "string",
					       "confidence": <number 0-1>,
					       "estimated_grams": <number>,
					       "coverage_percent": <number>,
					       "height_category": "FLAT" | "MEDIUM_PILE" | "HEEPING",
					       "bounding_box": { "x_pct": <number>, "y_pct": <number>, "w_pct": <number>, "h_pct": <number> },
					       "usda_search_terms": ["string"],
					       "visual_state": "RAW" | "COOKED" | "PROCESSED",
					       "texture_type": "SOLID" | "GRANULAR" | "LEAFY" | "MIXED"

					     }
					   ],
					   "warnings": ["any quality observations here"]
					}
					"""
					.formatted(refInstructions);
		}

		// --- MEDICAL GRADE PROMPT ---
		String referenceInstruction;
		if (isCard) {
			referenceInstruction = "- REFERENCE OBJECT: Look for a CREDIT CARD / ID CARD near the food. Known dimensions: "
					+ ReferenceObjectDimensions.CARD.getLengthCm() + "cm × "
					+ ReferenceObjectDimensions.CARD.getWidthCm()
					+ "cm. Use this known size to accurately estimate food volume.";
		} else if (referenceObjectType != null && (referenceObjectType.toLowerCase().contains("syringe")
				|| referenceObjectType.toLowerCase().contains("pen"))) {
			referenceInstruction = "- REFERENCE OBJECT: Look for an INSULIN SYRINGE or PEN near the food. Known dimensions: "
					   + ReferenceObjectDimensions.INSULIN_PEN.getLengthCm() + "cm length × "
			            + ReferenceObjectDimensions.INSULIN_PEN.getWidthCm()
					+ "cm width. Use this known size to accurately estimate food volume.";
		} else {
			referenceInstruction = "- REFERENCE OBJECT: Look for any known-size object near the food (pen, card, cutlery). IF FOUND, use it as a scale reference.";
		}

		return """
				                    You are a Professional Nutritionist AI.
				                    Your goal is to identify ALL food items in the image and estimate their portions for metabolic impact.

				                    MEAL ANALYSIS PROTOCOL:
				                    - Identify every distinct food item. A plate with rice and chicken = 2 items.
				                    - It is expected to see medical tools (syringes) or cards near the food for scale. Identify the FOOD, and ignore these reference objects in your final list.
				                    - Be PROACTIVE: If a food is partially visible or blurry, use context to provide your best estimate rather than returning empty results.
				                    - ONLY return an empty items list if the image truly contains NO food (e.g. just a wall or floor).
				                    - If unsure about a specific dish name, label it by its primary component (e.g. "Mixed Vegetables", "Grains").

				                    PROTOCOL:
				                    1. EVIDENCE ONLY: Do not guess ingredients you cannot see. If a sauce is visible but unknown, flag it.
				                    2. STATE ANALYSIS: Distinguish between RAW, BOILED, ROASTED, and FRIED. This critically affects Glycemic Index.
				                    3. BASE INGREDIENT: Separate the full description (e.g. 'Mashed Potatoes with Gravy') from the search term (e.g. 'Potato').
				                    4. ITEM SEPARATION: Break the plate into INDIVIDUAL food items. "Rice with chicken and salad" must become THREE items: "Rice", "Chicken", "Salad".

				                    CONTAINER & FILL ANALYSIS (CRITICAL FOR ACCURATE PORTIONS):
				                    - Identify the container type: FLAT_PLATE, REGULAR_BOWL, or DEEP_BOWL
				                    - Estimate what percentage (0-100) of the container's USABLE VOLUME is filled.
				                    - BOWL MATH RULE: In curved bowls, VOLUME decreases faster than height as you go down.
				                      * If food is halfway up the wall (50%% height), volume is only ~30%%.
				                      * If food is 75%% up the wall, volume is ~55%%.
				                    - CALIBRATION GUIDE:
				                      * 15 = thin layer at the bottom
				                      * 30 = halfway up the height of a curved bowl
				                      * 50 = significantly filled, but clearly 2-3cm below the rim
				                      * 70 = near the rim (approx 1cm gap)
				                      * 85 = level with the rim
				                      * 95+ = heaping / overflowing
				                    - IMPORTANT: Be conservative. If unsure, 40-50%% is a safe default for a normal serving.
				                    - Imagine the container empty, then imagine how much space the food occupies.

				                    PORTION ESTIMATION (CRITICAL):
				                    %s
				                    - FALLBACK (NO REFERENCE): If no reference object is visible, perform a "best-effort" estimation assuming a standard dinner plate size (approx 26cm diameter).
				                    - DO NOT FAIL simply because a reference object is missing. Always provide an estimated weight (grams) based on visual volume.

				                    CRITICAL NEGATIVE CONSTRAINTS:
				                    - Do NOT create a "Main Dish" or "Summary" item that combines others.
				                    - Do NOT list "Chicken and Rice" if you have already listed "Chicken" and "Rice" separately.
				                    - Each food pixel in the image should belong to EXACTLY ONE item in your list. Do not double count.

				              SPATIAL ANALYSIS (CRITICAL FOR ACCURACY):
				                    For each food item, estimate:
				                    - coverage_percent: What percentage of the plate/bowl surface this food occupies (all items should sum to ~100).
				                    - height_category: How tall the food is piled:
				                      * FLAT = thin layer, under 0.5cm (e.g. sauce, spread, single slice)
				                      * LOW_PILE = 0.5-1.5cm (e.g. steak, flat pasta, single layer)
				                      * MEDIUM_PILE = 1.5-3cm (e.g. rice serving, stir fry)
				                      * HIGH_PILE = 3cm+ (e.g. heaped salad, fries pile, overflowing rice)
				                      - texture_type: How the food physically occupies space (affects density calculation):
										* SOLID = whole/dense food with no air gaps (e.g. whole tomato, chicken breast, steak, egg, whole fruit)
				* GRANULAR = loose grains/pieces with air gaps between them (e.g. rice, pasta, couscous, chopped vegetables, beans)
				* LEAFY = very airy food, mostly air (e.g. salad leaves, spinach, herbs, shredded cabbage)
				* MIXED = combination or sauce-based dish (e.g. stew, stir-fry, soup, curry)
				* IMPORTANT: A whole tomato = SOLID. Diced tomatoes = GRANULAR. Tomato sauce = MIXED.
				                    - bounding_box: A tight rectangle around ONLY this food item, as percentage of the FULL IMAGE (not the plate).
				                      * x_pct = left edge (0 = left side of image, 100 = right side)
				                      * y_pct = top edge (0 = top of image, 100 = bottom)
				                      * w_pct = width of the box as %% of image width
				                      * h_pct = height of the box as %% of image height
				                      * Must tightly contain ONLY this specific food, not the whole plate.
				                    - usda_search_terms: Provide exactly 2-3 search terms for the USDA FoodData Central database.
				                      * First term: most specific match including cooking method (e.g. "rice white cooked")
				                      * Second term: broader match (e.g. "rice cooked")
				                      * Third term (optional): base ingredient only (e.g. "rice")

				                    OUTPUT FORMAT (Strict JSON):
				                    {
				                      "container_type": "REGULAR_BOWL",
				                      "container_fill_percent": 50,
				                      "items": [
				                        {
				                          "visual_name": "Roasted Potato Wedges",
				                          "base_ingredient": "Potato",
				                          "confidence": 0.95,
				                          "estimated_grams": 150,
				              	  "coverage_percent": 45,
				                          "height_category": "MEDIUM_PILE",
				                          "bounding_box": { "x_pct": 10.0, "y_pct": 25.0, "w_pct": 35.0, "h_pct": 40.0 },
				                          "usda_search_terms": ["potato wedges roasted", "potato roasted", "potato"],
				                          "visual_state": "ROASTED",
				                          "texture_type": "SOLID",
				                          "risk_flags": ["HIGH_FAT"],
				                          "requires_user_validation": false
				                        }
				                      ],
				                      "warnings": []
				                    }

				                    RULES:
				                    - If packaging text is visible, prioritize it over visual appearance.
				                    - If the food is blurry or unidentifiable, make a best-effort estimate of the major food group (e.g., 'Grains', 'Vegetables') based on context. Do not hallucinate brand names, but identify the base ingredient if possible.
				                    """
				.formatted(referenceInstruction);
	}

	private FoodRecognitionResult parseFoodsFromOpenAi(String rawResponse) throws Exception {
		String jsonText = extractFirstJsonObject(rawResponse);

		JsonNode root = objectMapper.readTree(jsonText);
		JsonNode items = root.get("items");

		// v2: Extract container-level fields
		FoodRecognitionResult result = new FoodRecognitionResult();
		if (root.has("container_fill_percent")) {
			result.setContainerFillPercent((float) root.get("container_fill_percent").asDouble());
		}
		if (root.has("container_type")) {
			result.setDetectedContainerType(root.get("container_type").asText());
		}

		List<FoodRecognitionResult.RecognizedFoodItem> results = new ArrayList<>();
		if (items != null && items.isArray()) {
			for (JsonNode item : items) {
				// 1. Basic Fields extraction
				String visualName = item.has("visual_name") ? item.get("visual_name").asText()
						: (item.has("name") ? item.get("name").asText() : "unknown");

				if (visualName.isEmpty() || "unknown".equalsIgnoreCase(visualName))
					continue;

				float conf = item.has("confidence") ? (float) item.get("confidence").asDouble() : 0.0f;

				Float weight = null;
				if (item.has("estimated_grams"))
					weight = (float) item.get("estimated_grams").asDouble();
				else if (item.has("estimatedPortionGrams"))
					weight = (float) item.get("estimatedPortionGrams").asDouble();

				// 2. New Medical Fields extraction
				String baseIngredient = item.has("base_ingredient") ? item.get("base_ingredient").asText() : visualName;
				String state = item.has("visual_state") ? item.get("visual_state").asText() : "UNKNOWN";
				boolean needsValidation = item.has("requires_user_validation")
						&& item.get("requires_user_validation").asBoolean();

				List<String> risks = new ArrayList<>();
				if (item.has("risk_flags") && item.get("risk_flags").isArray()) {
					for (JsonNode risk : item.get("risk_flags")) {
						risks.add(risk.asText());
					}
				}

				// 3. Construct the Object
				FoodRecognitionResult.RecognizedFoodItem recognizedItem = new FoodRecognitionResult.RecognizedFoodItem(
						visualName, conf, weight);

				// Set the new fields
				recognizedItem.setBaseIngredient(baseIngredient);
				recognizedItem.setVisualState(state);
				recognizedItem.setRequiresValidation(needsValidation);
				recognizedItem.setRiskFlags(risks);

				// Spatial fields for physics-based calculation
				if (item.has("coverage_percent")) {
					recognizedItem.setCoveragePercent((float) item.get("coverage_percent").asDouble());
				}

				if (item.has("height_category")) {
					recognizedItem.setHeightCategory(item.get("height_category").asText());
				}
				
				if (item.has("texture_type")) {
				    recognizedItem.setTextureType(item.get("texture_type").asText());
				}
				
				// bounding box for client-side GrabCut segmentation
				if (item.has("bounding_box")) {
					JsonNode bbox = item.get("bounding_box");
					recognizedItem.setBoundingBox((float) bbox.path("x_pct").asDouble(0),
							(float) bbox.path("y_pct").asDouble(0), (float) bbox.path("w_pct").asDouble(0),
							(float) bbox.path("h_pct").asDouble(0));
				}

				// Gemini-provided USDA search terms (replaces hardcoded FoodNameNormalizer)
				List<String> searchTerms = new ArrayList<>();
				if (item.has("usda_search_terms") && item.get("usda_search_terms").isArray()) {
					for (JsonNode term : item.get("usda_search_terms")) {
						searchTerms.add(term.asText());
					}
				}
				recognizedItem.setUsdaSearchTerms(searchTerms);

				results.add(recognizedItem);
			}
		}
		result.setDetectedFoods(results);
		return result;
	}

	// private String extractContentFromResponse(String rawResponse) {
	// return jsonParser.extractContent(rawResponse);
	// }

	private String extractFirstJsonObject(String raw) {
		return jsonParser.extractJsonObject(raw);
	}
}