package com.insuscan.boundary;

public class ChatParseRequest {

    private String text;
    private String state;

    // Constructors
    public ChatParseRequest() {
    }

    public ChatParseRequest(String text, String state) {
        this.text = text;
        this.state = state;
    }

    // Getters and Setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "ChatParseRequest{text='" + text + "', state='" + state + "'}";
    }
}
