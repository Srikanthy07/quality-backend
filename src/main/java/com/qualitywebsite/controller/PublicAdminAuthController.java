package com.qualitywebsite.controller;

import com.qualitywebsite.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/admin")
@RequiredArgsConstructor
public class PublicAdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        adminAuthService.requestPasswordReset(email);
        // Always return generic response to prevent user enumeration
        return ResponseEntity.ok(Map.of(
                "message", "If an active administrator account exists with that email address, a password reset link has been dispatched."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        String newPassword = payload.get("newPassword");
        String confirmPassword = payload.get("confirmPassword");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password reset token is missing."));
        }
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password cannot be blank."));
        }
        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password and confirm password do not match."));
        }

        try {
            adminAuthService.resetPassword(token, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password successfully reset. You may now log in with your new password."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
