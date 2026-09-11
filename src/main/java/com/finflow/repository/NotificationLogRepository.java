package com.finflow.repository;

import com.finflow.entity.NotificationLog;
import com.finflow.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByStatus(NotificationStatus status);
}
