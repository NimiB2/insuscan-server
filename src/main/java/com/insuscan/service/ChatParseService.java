package com.insuscan.service;

import com.insuscan.boundary.ChatParseRequest;
import com.insuscan.boundary.ChatParseResponse;

public interface ChatParseService {
    ChatParseResponse parseUserText(ChatParseRequest request);

    boolean isAvailable();
}
