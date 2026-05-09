package com.hcl.hackathon.notificationservice.service;

import com.hcl.hackathon.notificationservice.email.EmailTemplateGenerator;
import com.hcl.hackathon.notificationservice.email.EmailTemplateGenerator.EmailContent;
import com.hcl.hackathon.notificationservice.kafka.KafkaMessageContext;
import com.hcl.hackathon.notificationservice.kafka.MdcContextManager;
import com.hcl.hackathon.notificationservice.model.ApplicationStatusEvent;
import com.hcl.hackathon.notificationservice.util.DataMaskingUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends applicant emails for credit-card application status changes and
 * records each delivery attempt in {@code notification_log}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final EmailTemplateGenerator emailTemplateGenerator;
    private final DataMaskingUtil maskingUtil;
    private final NotificationLogService notificationLogService;

    @Value("${spring.mail.from:noreply@creditcard.com}")
    private String fromAddress;

    public void processNotification(ApplicationStatusEvent event, KafkaMessageContext kafkaContext) {
        String maskedId = maskingUtil.maskApplicationId(event.getApplicationId());
        String correlationId = MDC.get(MdcContextManager.MDC_CORRELATION_ID_KEY);

        log.info("Processing notification | applicationId={} | status={} | customer={}",
                maskedId,
                event.getStatus(),
                maskingUtil.maskEmail(event.getEmail()));

        try {
            EmailContent content = emailTemplateGenerator.generate(event);
            sendHtmlEmail(event.getEmail(), content);
            notificationLogService.recordSuccess(event, kafkaContext, correlationId);

            log.info("Notification sent successfully | applicationId={} | status={}",
                    maskedId, event.getStatus());

        } catch (MailException | MessagingException ex) {
            log.error("Failed to send notification | applicationId={} | status={} | error={}",
                    maskedId, event.getStatus(), ex.getMessage(), ex);
            notificationLogService.recordFailure(event, kafkaContext, correlationId, ex.getMessage());
            throw new NotificationException(
                    "Email delivery failed for applicationId: " + maskedId, ex);
        }
    }

    private void sendHtmlEmail(String to, EmailContent content) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(content.subject());
        helper.setText(content.htmlBody(), true);
        mailSender.send(mimeMessage);
    }

    public static class NotificationException extends RuntimeException {
        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
