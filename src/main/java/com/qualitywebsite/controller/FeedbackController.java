package com.qualitywebsite.controller;

import com.qualitywebsite.dto.FeedbackRequest;
import com.qualitywebsite.service.EmailService;
import jakarta.mail.AuthenticationFailedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    private final EmailService emailService;

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @Valid @RequestBody FeedbackRequest request) {
        try {
            emailService.sendFeedbackEmail(request);
            log.info("Feedback submitted successfully from: {}", request.getEmail());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Thank you for your feedback! We'll review it shortly."
            ));

        } catch (MailAuthenticationException e) {
            // FIX: Catches the specific failure when spring.mail.password is wrong/blank.
            // Original code caught generic Exception and returned the same message for all
            // failures — this makes debugging impossible.
            log.error("SMTP authentication failed — check spring.mail.password in application.properties: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Mail server authentication failed. Please contact the administrator."
            ));

        } catch (MailSendException e) {
            log.error("Failed to send feedback email to recipients: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Unable to send the email right now. Please try again in a few moments."
            ));

        } catch (Exception e) {
            log.error("Unexpected error sending feedback from {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Something went wrong. Please try again later."
            ));
        }
    }
}