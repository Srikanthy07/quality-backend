package com.qualitywebsite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email address.")
    private String email;

    @NotBlank(message = "Message is required.")
    @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters.")
    private String message;
}