package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.ChatParseRequest;
import com.insuscan.boundary.ChatParseResponse;
import com.insuscan.util.OpenAiJsonParser;
import com.insuscan.util.GeminiApiClient;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatParseServiceImpl implements ChatParseService {

	private static final Logger log = LoggerFactory.getLogger(ChatParseServiceImpl.class);

	private final GeminiApiClient geminiClient;
	private final ObjectMapper objectMapper;
	private final OpenAiJsonParser jsonParser;

	public ChatParseServiceImpl(GeminiApiClient geminiClient, ObjectMapper objectMapper,
			OpenAiJsonParser jsonParser) {
		this.geminiClient = geminiClient;
		this.objectMapper = objectMapper;
		this.jsonParser = jsonParser;
	}

	@Override
	public boolean isAvailable() {
//		return openAiApiKey != null && !openAiApiKey.isBlank();
		return geminiClient.isServiceAvailable();

	}

	@Override
	public ChatParseResponse parseUserText(ChatParseRequest request) {
		if (!isAvailable()) {
			log.warn("[CHAT-PARSE] OpenAI not available");
			return fallbackResponse(request.getText());
		}

		long startTime = System.currentTimeMillis();
		log.info("[CHAT-PARSE] Parsing: '{}' (state={})", request.getText(), request.getState());

//		try {
//			String prompt = buildPrompt(request.getText(), request.getState());
//
//			Map<String, Object> body = Map.of(
//                    "model", openAiModel,
//                    "messages", List.of(
//                            Map.of("role", "system", "content", SYSTEM_PROMPT),
//                            Map.of("role", "user", "content", prompt)),
//                    "temperature", 0.2,
//                    "response_format", Map.of("type", "json_object"));
//
//			String response = webClient.post().uri("/chat/completions")
//					.header("Authorization", "Bearer " + openAiApiKey).header("Content-Type", "application/json")
//					.bodyValue(body).retrieve().bodyToMono(String.class).block();
//
//			long elapsed = System.currentTimeMillis() - startTime;
//			log.info("[CHAT-PARSE] Response received in {}ms", elapsed);
//
//			return parseResponse(response);
//
//		} catch (Exception e) {
		try {
			String prompt = buildPrompt(request.getText(), request.getState());

			String content = geminiClient.callFlashModelWithSystem(SYSTEM_PROMPT, prompt);

			long elapsed = System.currentTimeMillis() - startTime;
			log.info("[CHAT-PARSE] Response received in {}ms", elapsed);

			return parseResponseDirect(content);

		} catch (Exception e) {
			log.error("[CHAT-PARSE] Error: {}", e.getMessage());
			return fallbackResponse(request.getText());
		}
	}

	// ===== PROMPT =====

	private static final String SYSTEM_PROMPT = """
			You are a meal assistant AI for a diabetes management app called InsuScan.
			Your job is to understand what the user is saying and return a structured JSON response.

			You MUST return valid JSON only. No extra text.

			Possible actions:
			1. "add_food" - The user describes food they ate. Extract food items with names, quantities, and estimated carbs per item.
			   * If the user says "I also ate...", use this action. The app will merge it.
			2. "set_glucose" - The user provides their blood glucose level.
			3. "set_activity" - The user mentions exercise or activity. Map to: "normal", "light", or "intense".
			4. "set_medical_params" - The user wants to change insulin settings. Fields: icr, isf, targetGlucose.
			5. "confirm" - The user wants to confirm/proceed.
			6. "clarify" - You need more information (e.g., "how many eggs?") BEFORE adding food.
			    * Use this if the user mentions a food item without any quantity (e.g. "I had rice" -> clarify "How much rice? e.g. 1 cup, 150g").
			    * When clarifying, explicitly suggest relevant units for that food type (e.g. "slices" for bread, "cups" for rice/pasta, "grams" for snacks).
			    * Do NOT assume standard servings unless the user implies it (e.g. "an apple").
			7. "unknown" - You can't determine the intent.

			Response format:

			For food:
			{
			  "action": "add_food",
			  "items": [{"name": "Rice", "quantity": 1, "estimatedCarbsGrams": 45.0}],
			  "message": "I added 1 cup of rice (45g carbs)."
			}

			For medical params (e.g. "Change ICR to 15"):
			{
			  "action": "set_medical_params",
			  "icr": 15.0,
			  "message": "Updating insulin-to-carb ratio to 1:15"
			}
			(You can set isf, icr, or targetGlucose independently)

			For clarification:
			{ "action": "clarify", "message": "How much rice did you have?" }

			IMPORTANT RULES:
			- For food items, estimate carbs based on common nutritional data
			- Support Hebrew and English text
			- Be generous in carb estimation
			- Quantity defaults to 1 if not specified
			""";

	private String buildPrompt(String text, String state) {
		return String.format("""
				User input: "%s"
				Current chat state: %s

				Parse this input and return the appropriate JSON response.
				""", text, state != null ? state : "UNKNOWN");
	}

	// ===== RESPONSE PARSING =====

//	private ChatParseResponse parseResponse(String rawResponse) {
//		try {
//			JsonNode parsed = jsonParser.parseContent(rawResponse);
//
//			ChatParseResponse response = new ChatParseResponse();
//			response.setAction(parsed.has("action") ? parsed.get("action").asText() : "unknown");
//			response.setMessage(parsed.has("message") ? parsed.get("message").asText() : "");
//
//			// Parse food items
//			if ("add_food".equals(response.getAction()) && parsed.has("items") && parsed.get("items").isArray()) {
//				List<ChatParseResponse.FoodEntry> items = new ArrayList<>();
//				for (JsonNode itemNode : parsed.get("items")) {
//					ChatParseResponse.FoodEntry entry = new ChatParseResponse.FoodEntry();
//					entry.setName(itemNode.has("name") ? itemNode.get("name").asText() : "Unknown");
//					entry.setQuantity(itemNode.has("quantity") ? itemNode.get("quantity").asInt() : 1);
//					entry.setEstimatedCarbsGrams(
//							itemNode.has("estimatedCarbsGrams") ? (float) itemNode.get("estimatedCarbsGrams").asDouble()
//									: 0f);
//					items.add(entry);
//				}
//				response.setItems(items);
//			}
//
//			// Parse glucose
//			if ("set_glucose".equals(response.getAction()) && parsed.has("glucose")) {
//				response.setGlucose(parsed.get("glucose").asInt());
//			}
//
//			// Parse activity
//			if ("set_activity".equals(response.getAction()) && parsed.has("activity")) {
//				response.setActivity(parsed.get("activity").asText());
//			}
//
//			// Parse medical params
//			if ("set_medical_params".equals(response.getAction())) {
//				if (parsed.has("icr"))
//					response.setIcr(parsed.get("icr").asDouble());
//				if (parsed.has("isf"))
//					response.setIsf(parsed.get("isf").asDouble());
//				if (parsed.has("targetGlucose"))
//					response.setTargetGlucose(parsed.get("targetGlucose").asInt());
//			}
//
//			log.info("[CHAT-PARSE] Parsed action='{}', message='{}'", response.getAction(), response.getMessage());
//			return response;
//
//		} catch (Exception e) {
//			log.error("[CHAT-PARSE] Failed to parse response: {}", e.getMessage());
//			return fallbackResponse("Error parsing response");
//		}
//	}
	
	private ChatParseResponse parseResponseDirect(String content) {
		if (content == null || content.isBlank()) {
			return fallbackResponse("Empty AI response");
		}
		try {
			String json = jsonParser.extractJsonObject(content);
			JsonNode parsed = objectMapper.readTree(json);

			ChatParseResponse response = new ChatParseResponse();
			response.setAction(parsed.has("action") ? parsed.get("action").asText() : "unknown");
			response.setMessage(parsed.has("message") ? parsed.get("message").asText() : "");

			if ("add_food".equals(response.getAction()) && parsed.has("items") && parsed.get("items").isArray()) {
				List<ChatParseResponse.FoodEntry> items = new ArrayList<>();
				for (JsonNode itemNode : parsed.get("items")) {
					ChatParseResponse.FoodEntry entry = new ChatParseResponse.FoodEntry();
					entry.setName(itemNode.has("name") ? itemNode.get("name").asText() : "Unknown");
					entry.setQuantity(itemNode.has("quantity") ? itemNode.get("quantity").asInt() : 1);
					entry.setEstimatedCarbsGrams(
							itemNode.has("estimatedCarbsGrams") ? (float) itemNode.get("estimatedCarbsGrams").asDouble() : 0f);
					items.add(entry);
				}
				response.setItems(items);
			}

			if ("set_glucose".equals(response.getAction()) && parsed.has("glucose")) {
				response.setGlucose(parsed.get("glucose").asInt());
			}

			if ("set_activity".equals(response.getAction()) && parsed.has("activity")) {
				response.setActivity(parsed.get("activity").asText());
			}

			if ("set_medical_params".equals(response.getAction())) {
				if (parsed.has("icr")) response.setIcr(parsed.get("icr").asDouble());
				if (parsed.has("isf")) response.setIsf(parsed.get("isf").asDouble());
				if (parsed.has("targetGlucose")) response.setTargetGlucose(parsed.get("targetGlucose").asInt());
			}

			log.info("[CHAT-PARSE] Parsed action='{}', message='{}'", response.getAction(), response.getMessage());
			return response;

		} catch (Exception e) {
			log.error("[CHAT-PARSE] Failed to parse: {}", e.getMessage());
			return fallbackResponse("Error parsing response");
		}
	}

	private ChatParseResponse fallbackResponse(String text) {
		ChatParseResponse response = new ChatParseResponse();
		response.setAction("unknown");
		response.setMessage("I'm not sure what you mean by \"" + text
				+ "\". Try describing your meal or providing your glucose level.");
		return response;
	}
}
