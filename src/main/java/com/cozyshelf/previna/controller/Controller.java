package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
@Tag(name = "Base Controller")
public class Controller {
    @GetMapping()
    @Operation(summary = "Check if API is working")
    public ResponseEntity<Response> check() {
        return ResponseEntity.ok(new Response("What's up folks ? Are you fine ? This API is working like never before"));
    }
}
