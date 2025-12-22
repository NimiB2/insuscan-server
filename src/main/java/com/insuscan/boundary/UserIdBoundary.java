package com.insuscan.boundary;

// User identifier containing email and system ID
public class UserIdBoundary {
    private String email;
    private String systemId;
    
    public UserIdBoundary() {}
    
    public UserIdBoundary(String email, String systemId) {
        this.email = email;
        this.systemId = systemId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    @Override
    public String toString() {
        return "UserIdBoundary{email='" + email + "', systemId='" + systemId + "'}";
    }
}
