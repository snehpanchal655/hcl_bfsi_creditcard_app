package com.hcl.hackathon.notificationservice.persistence;

import com.hcl.hackathon.notificationservice.persistence.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
