package com.finflow.service;

import com.finflow.entity.NotificationLog;
import com.finflow.entity.Transaction;
import com.finflow.entity.User;
import com.finflow.enums.NotificationChannel;
import com.finflow.enums.NotificationStatus;
import com.finflow.enums.NotificationType;
import com.finflow.notification.EmailNotificationProvider;
import com.finflow.notification.SmsProvider;
import com.finflow.repository.NotificationLogRepository;
import com.finflow.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TransactionRepository transactionRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailNotificationProvider emailProvider;
    private final SmsProvider smsProvider;

    public NotificationService(
            TransactionRepository transactionRepository,
            NotificationLogRepository notificationLogRepository,
            EmailNotificationProvider emailProvider,
            SmsProvider smsProvider) {
        this.transactionRepository = transactionRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
    }

    @Async("notificationExecutor")
    @Transactional
    public void send(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            log.warn("[NOTIFICATION] Transaction {} no longer exists", transactionId);
            return;
        }

        notifyUser(
                transaction,
                transaction.getFromWallet().getUser(),
                NotificationType.TRANSFER_SENT,
                "Transfer sent",
                "You sent " + money(transaction.getAmount()) +
                        " from wallet " + transaction.getFromWallet().getWalletNumber() +
                        " to " + transaction.getToWallet().getWalletNumber() + "."
        );

        notifyUser(
                transaction,
                transaction.getToWallet().getUser(),
                NotificationType.TRANSFER_RECEIVED,
                "Transfer received",
                "You received " + money(transaction.getAmount()) +
                        " in wallet " + transaction.getToWallet().getWalletNumber() +
                        " from " + transaction.getFromWallet().getUser().getUsername() + "."
        );
    }

    private void notifyUser(
            Transaction transaction,
            User user,
            NotificationType type,
            String subject,
            String text) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            deliverEmail(transaction, user, type, subject, text);
        }

        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            deliverSms(transaction, user, type, subject, text);
        }
    }

    private void deliverEmail(
            Transaction transaction,
            User user,
            NotificationType type,
            String subject,
            String text) {
        NotificationLog record = createRecord(
                transaction,
                user,
                NotificationChannel.EMAIL,
                type,
                user.getEmail(),
                subject,
                text
        );

        if (!emailProvider.isEnabled()) {
            markSkipped(record, "Email provider is not configured");
            return;
        }

        try {
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setProviderMessageId(emailProvider.send(
                    user.getEmail(),
                    subject,
                    text,
                    htmlMessage(subject, text),
                    "finflow-notification-" + record.getId()
            ));
            record.setStatus(NotificationStatus.SENT);
            record.setSentAt(LocalDateTime.now());
            notificationLogRepository.save(record);
        } catch (Exception ex) {
            markFailed(record, ex);
        }
    }

    private void deliverSms(
            Transaction transaction,
            User user,
            NotificationType type,
            String subject,
            String text) {
        NotificationLog record = createRecord(
                transaction,
                user,
                NotificationChannel.SMS,
                type,
                user.getPhoneNumber(),
                subject,
                text
        );

        if (!smsProvider.isEnabled()) {
            markSkipped(record, "SMS provider is not configured");
            return;
        }

        try {
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setProviderMessageId(smsProvider.send(
                    user.getPhoneNumber(),
                    "FinFlow: " + text
            ));
            record.setStatus(NotificationStatus.SENT);
            record.setSentAt(LocalDateTime.now());
            notificationLogRepository.save(record);
        } catch (Exception ex) {
            markFailed(record, ex);
        }
    }

    private NotificationLog createRecord(
            Transaction transaction,
            User user,
            NotificationChannel channel,
            NotificationType type,
            String recipient,
            String subject,
            String content) {
        NotificationLog record = new NotificationLog();
        record.setTransaction(transaction);
        record.setUser(user);
        record.setChannel(channel);
        record.setType(type);
        record.setRecipient(recipient);
        record.setSubject(subject);
        record.setContent(content);
        return notificationLogRepository.saveAndFlush(record);
    }

    private void markSkipped(NotificationLog record, String reason) {
        record.setStatus(NotificationStatus.SKIPPED);
        record.setFailureReason(reason);
        notificationLogRepository.save(record);
        log.info("[NOTIFICATION] Skipped {} delivery to {}: {}",
                record.getChannel(), record.getRecipient(), reason);
    }

    private void markFailed(NotificationLog record, Exception ex) {
        record.setStatus(NotificationStatus.FAILED);
        record.setFailureReason(ex.getMessage());
        notificationLogRepository.save(record);
        log.error("[NOTIFICATION] {} delivery failed for {}: {}",
                record.getChannel(), record.getRecipient(), ex.getMessage());
    }

    private String money(BigDecimal amount) {
        return "$" + amount.setScale(2);
    }

    private String htmlMessage(String subject, String text) {
        return "<div style='font-family:Arial,sans-serif;color:#172033'>"
                + "<h2>" + escape(subject) + "</h2>"
                + "<p>" + escape(text) + "</p>"
                + "<p style='color:#64748b'>FinFlow secure payments</p>"
                + "</div>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
