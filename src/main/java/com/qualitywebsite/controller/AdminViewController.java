package com.qualitywebsite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminViewController {

    @GetMapping({"", "/", "/login"})
    public String loginPage() {
        return "admin/login";
    }

    @GetMapping("/dashboard")
    public String dashboardPage() {
        return "admin/dashboard";
    }

    @GetMapping("/documents")
    public String documentsPage() {
        return "admin/documents";
    }

    @GetMapping("/documents/upload")
    public String uploadPage() {
        return "admin/documents-upload";
    }

    @GetMapping("/generic-templates")
    public String genericTemplatesPage() {
        return "admin/generic-templates";
    }

    @GetMapping("/lessons-learned")
    public String lessonsLearnedPage() {
        return "admin/lessons-learned";
    }

    @GetMapping("/assessment-checklists")
    public String assessmentChecklistsPage() {
        return "admin/assessment-checklists";
    }

    @GetMapping("/master-list")
    public String masterListPage() {
        return "admin/master-list";
    }

    @GetMapping("/settings")
    public String settingsPage() {
        return "admin/settings";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "admin/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "admin/reset-password";
    }

    @GetMapping("/website-analytics")
    public String websiteAnalyticsPage() {
        return "admin/website-analytics";
    }
}
