package com.qualitywebsite.service;

import com.qualitywebsite.entity.Setting;
import com.qualitywebsite.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingRepository settingRepository;
    private final ActivityLogService activityLogService;

    @PostConstruct
    public void initDefaultSettings() {
        initIfMissing("website_title", "IAST Quality Portal");
        initIfMissing("company_logo", "/images/logo.png");
        initIfMissing("footer_text", "© IAST Software Solutions. All rights reserved.");
        initIfMissing("contact_email", "quality@iast.com");
        initIfMissing("smtp_host", "smtp.office365.com");
        initIfMissing("smtp_port", "587");
        initIfMissing("smtp_username", "");
        initIfMissing("smtp_password", "");
    }

    private void initIfMissing(String key, String defaultValue) {
        if (!settingRepository.existsById(key)) {
            settingRepository.save(new Setting(key, defaultValue));
        }
    }

    public Map<String, String> getAllSettings() {
        List<Setting> settings = settingRepository.findAll();
        Map<String, String> map = new HashMap<>();
        for (Setting s : settings) {
            if ("smtp_password".equalsIgnoreCase(s.getSettingKey())) {
                String val = s.getSettingValue();
                map.put(s.getSettingKey(), (val != null && !val.trim().isEmpty()) ? "********" : "");
            } else {
                map.put(s.getSettingKey(), s.getSettingValue());
            }
        }
        return map;
    }

    public String getSetting(String key, String defaultValue) {
        return settingRepository.findById(key)
                .map(Setting::getSettingValue)
                .orElse(defaultValue);
    }

    public void updateSettings(Map<String, String> settingsMap, String username) {
        settingsMap.forEach((k, v) -> {
            if ("smtp_password".equalsIgnoreCase(k)) {
                if (v != null && (v.equals("********") || v.equals("••••••••"))) {
                    return; // Do not overwrite existing DB password with masked string
                }
            }
            settingRepository.save(new Setting(k, v != null ? v : ""));
        });
        activityLogService.logActivity(username, "Updated Settings", "Portal settings updated");
    }
}
