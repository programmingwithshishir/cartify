package com.cartify.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private final UserRepository userRepository;

    public AuthApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        if (request.fullName() == null || request.fullName().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "All fields are required."));
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email is already registered."));
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(request.password()); // Stored as plain text per current requirement.
        user.setRole(UserRole.CUSTOMER);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Registration successful."));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, String>> customerLogin(@RequestBody LoginRequest request, HttpSession session) {
        if (request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required."));
        }

        return userRepository.findByEmailAndRole(request.email().trim().toLowerCase(), UserRole.CUSTOMER)
                .filter(user -> user.getPassword().equals(request.password()))
                .map(user -> {
                    session.setAttribute("customerId", user.getId());
                    session.setAttribute("customerEmail", user.getEmail());
                    session.setAttribute("customerName", user.getFullName());
                    session.setAttribute("isAdmin", false);
                    return ResponseEntity.ok(Map.of("message", "Customer login successful."));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid customer credentials.")));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<Map<String, String>> adminLogin(@RequestBody LoginRequest request, HttpSession session) {
        if (request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username and password are required."));
        }

        return userRepository.findByEmailAndRole(request.email().trim().toLowerCase(), UserRole.ADMIN)
                .filter(user -> user.getPassword().equals(request.password()))
                .map(admin -> {
                    session.setAttribute("isAdmin", true);
                    session.setAttribute("adminUsername", admin.getEmail());
                    return ResponseEntity.ok(Map.of("message", "Admin login successful."));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid admin credentials.")));
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<Map<String, String>> adminLogout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    public record RegisterRequest(String fullName, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }
}
