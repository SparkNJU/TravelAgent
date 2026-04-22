package org.example.backend.controller;

import org.example.backend.dto.LoginRequest;
import org.example.backend.dto.LoginResponse;
import org.example.backend.dto.RegisterRequest;
import org.example.backend.dto.RegisterResponse;
import org.example.backend.entity.User;
import org.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * User login
     */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        // Authenticate user from database
        Optional<User> user = userService.authenticate(request.getUsername(), request.getPassword());
        
        if (user.isPresent()) {
            String token = "token_" + System.currentTimeMillis() + "_" + user.get().getId();
            return new LoginResponse(true, "Authentication successful", token, user.get().getId());
        } else {
            return new LoginResponse(false, "Invalid username or password", null);
        }
    }

    /**
     * User registration
     */
    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        // Validate input
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return new RegisterResponse(false, "Username cannot be empty", null);
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return new RegisterResponse(false, "Password must be at least 6 characters", null);
        }
        if (request.getEmail() == null || !request.getEmail().contains("@")) {
            return new RegisterResponse(false, "Invalid email format", null);
        }

        // Check if username already exists
        if (userService.usernameExists(request.getUsername())) {
            return new RegisterResponse(false, "Username already exists", null);
        }

        // Check if email already exists
        if (userService.emailExists(request.getEmail())) {
            return new RegisterResponse(false, "Email already exists", null);
        }

        // Register new user
        try {
            User newUser = userService.registerUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    request.getPhone()
            );
            return new RegisterResponse(true, "Registration successful", newUser.getId());
        } catch (Exception e) {
            return new RegisterResponse(false, "Registration failed: " + e.getMessage(), null);
        }
    }
}
