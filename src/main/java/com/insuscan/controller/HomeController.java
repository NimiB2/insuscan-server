package com.insuscan.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class HomeController {

    /**
     * Root endpoint - serves the test vision HTML page
     * Access at: http://localhost:9693/
     */
    @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getHome() {
        Resource resource = new ClassPathResource("static/test-vision.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }
    
    /**
     * Alternative endpoint for the test page
     * Access at: http://localhost:9693/test-vision
     */
    @GetMapping(path = "/test-vision", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getTestVision() {
        Resource resource = new ClassPathResource("static/test-vision.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }
}
