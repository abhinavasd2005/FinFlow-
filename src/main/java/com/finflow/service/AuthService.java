package com.finflow.service;

import com.finflow.dto.request.LoginRequest;
import com.finflow.dto.request.RegisterRequest;
import com.finflow.dto.response.AuthResponse;
import com.finflow.entity.User;
import com.finflow.exception.AuthenticationFailedException;
import com.finflow.exception.ConflictException;
import com.finflow.mapper.EntityMapper;
import com.finflow.repository.UserRepository;
import com.finflow.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finflow.enums.Role;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return registerInternal(request, Role.USER);
    }
    @Transactional
    public AuthResponse registerAdmin(RegisterRequest request) {
        return registerInternal(request, Role.ADMIN);
    }
    private AuthResponse registerInternal(
            RegisterRequest request,
            Role role
    ) {
        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhoneNumber(normalizePhoneNumber(request.getPhoneNumber()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        String token =
                jwtUtil.generateToken(savedUser.getUsername());

        return new AuthResponse(
                token,
                EntityMapper.toUserResponse(savedUser)
        );
    }

    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        username,
                        request.getPassword()
                )
        );

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token, EntityMapper.toUserResponse(user));
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim();
    }
}
