package com.example.transporterlanes.controller;

import com.example.transporterlanes.dto.RootResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing endpoint for the service root, e.g. a browser visiting the deployed URL. Hidden from the OpenAPI document,
 * which describes only the assignment's two endpoints.
 */
@Hidden
@RestController
public class RootController {

    @GetMapping(path = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public RootResponse root() {
        return RootResponse.running();
    }
}
