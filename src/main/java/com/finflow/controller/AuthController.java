package com.finflow.controller;

import com.finflow.dto.request.LoginRequest;
import com.finflow.dto.request.RegisterRequest;
import com.finflow.dto.response.AuthResponse;
import com.finflow.exception.ForbiddenOperationException;
import com.finflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final String adminRegistrationSecret;

    public AuthController(
            AuthService authService,
            @Value("${admin.registration-secret:}") String adminRegistrationSecret) {
        this.authService = authService;
        this.adminRegistrationSecret = adminRegistrationSecret;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    @PostMapping("/register/admin")
    public ResponseEntity<AuthResponse> registerAdmin(
            @Valid @RequestBody RegisterRequest request,
            @RequestParam String adminSecret
    ) {

        if (adminRegistrationSecret.isBlank() ||
                !adminRegistrationSecret.equals(adminSecret)) {
            throw new ForbiddenOperationException("Invalid admin registration secret");
        }

        return ResponseEntity.status(201)
                .body(authService.registerAdmin(request));
    }
}
