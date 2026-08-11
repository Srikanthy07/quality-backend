package com.qualitywebsite.service;

import com.qualitywebsite.entity.ActivityLog;
import com.qualitywebsite.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public void logActivity(String username, String action, String details) {
        ActivityLog log = ActivityLog.builder()
                .username(username != null ? username : "admin")
                .action(action)
                .details(details)
                .build();
        activityLogRepository.save(log);
    }

    public List<ActivityLog> getRecentActivities() {
        return activityLogRepository.findTop20ByOrderByTimestampDesc();
    }
}
