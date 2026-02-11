package com.insuscan.controller;

import com.insuscan.boundary.ChatParseRequest;
import com.insuscan.boundary.ChatParseResponse;
import com.insuscan.service.ChatParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/chat")
public class ChatParseController {

    private static final Logger log = LoggerFactory.getLogger(ChatParseController.class);

    private final ChatParseService chatParseService;

    public ChatParseController(ChatParseService chatParseService) {
        this.chatParseService = chatParseService;
    }

    @PostMapping("/parse")
    public ResponseEntity<ChatParseResponse> parseUserText(@RequestBody ChatParseRequest request) {
        log.info("[CHAT] POST /chat/parse: {}", request);

        if (request.getText() == null || request.getText().isBlank()) {
            ChatParseResponse error = new ChatParseResponse("unknown", "Please enter some text.");
            return ResponseEntity.badRequest().body(error);
        }

        ChatParseResponse response = chatParseService.parseUserText(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        boolean available = chatParseService.isAvailable();
        return ResponseEntity
                .ok(available ? "Chat parse service is online" : "Chat parse service is offline (no API key)");
    }
}
