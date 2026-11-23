package com.example.transporterlanes.dto;

/** Body of GET /: confirms the service is up and points to the API documentation. */
public record RootResponse(String status, String message, String documentation) {

    public static RootResponse running() {
        return new RootResponse("success", "Transporter Lane Optimization API is running.", "/swagger-ui.html");
    }
}
