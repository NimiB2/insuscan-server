package com.insuscan.service;

import com.insuscan.boundary.ChatParseRequest;
import com.insuscan.boundary.ChatParseResponse;

/**
 * Parses free-text meal-related user input into structured chat actions (add food, set glucose, set medical params, etc.).
 */
public interface ChatParseService {
    ChatParseResponse parseUserText(ChatParseRequest request);

    boolean isAvailable();
}