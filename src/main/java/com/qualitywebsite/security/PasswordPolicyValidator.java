package com.qualitywebsite.security;

import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 12;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "admin123", "admin12345678", "password12345", "123456789012",
            "administrator1", "iastquality123", "quality123456"
    );

    public static void validate(String password) {
        if (!StringUtils.hasText(password) || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long.");
        }

        if (WEAK_PASSWORDS.contains(password.trim().toLowerCase())) {
            throw new IllegalArgumentException("Password is too common or weak. Please choose a stronger password.");
        }

        if (!UPPERCASE.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter (A-Z).");
        }

        if (!LOWERCASE.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter (a-z).");
        }

        if (!DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one digit (0-9).");
        }

        if (!SPECIAL.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one special character (e.g. !@#$%^&*).");
        }
    }
}
