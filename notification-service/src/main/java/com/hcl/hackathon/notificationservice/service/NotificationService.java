package com.creditcard.notification.service;

import com.creditcard.notification.email.EmailTemplateGenerator;
import com.creditcard.notification.email.EmailTemplateGenerator.EmailContent;
import com.creditcard.notification.model.ApplicationStatusEvent;
import com.creditcard.notification.util.DataMaskingUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the end-to-end notification flow:
 * <ol>
 *   <li>Generates the correct HTML email via {@link EmailTemplateGenerator}</li>
 *   <li>Sends it through {@link JavaMailSender}</li>
 *   <li>Logs every step using masked identifiers (never raw PII)</li>
 * </ol>
 *
 * <p><b>SRP:</b> This service owns only the "send notification" use-case.
 * Template logic lives in {@link EmailTemplateGenerator};
 * masking logic lives in {@link DataMaskingUtil};
 * retry / DLQ logic lives in the Kafka consumer configuration.
 *
 * <p><b>OCP / DIP:</b> Depends on abstractions ({@link JavaMailSender},
 * {@link EmailTemplateGenerator}) — not concrete implementations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender       mailSender;
    private final EmailTemplateGenerator emailTemplateGenerator;
    private final DataMaskingUtil      maskingUtil;

    @Value("${spring.mail.from:noreply@creditcard.com}")
    private String fromAddress;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Processes an {@link ApplicationStatusEvent} by generating and sending
     * the appropriate HTML email to the applicant.
     *
     * <p>Any {@link MailException} or {@link MessagingException} is wrapped and
     * re-thrown as a {@link NotificationException} so that the Kafka consumer
     * layer can decide whether to retry or route to the DLQ.
     *
     * @param event the event received from the Kafka {@code card.status} topic
     * @throws NotificationException if email composition or sending fails
     */
    public void processNotification(ApplicationStatusEvent event) {
        String maskedId = maskingUtil.maskApplicationId(event.getApplicationId());

        log.info("Processing notification | applicationId={} | status={} | customer={}",
                maskedId,
                event.getStatus(),
                maskingUtil.maskEmail(event.getEmail()));  // email also masked in logs

        try {
            EmailContent content = emailTemplateGenerator.generate(event);
            sendHtmlEmail(event.getEmail(), content);

            log.info("Notification sent successfully | applicationId={} | status={}",
                    maskedId, event.getStatus());

        } catch (MailException | MessagingException ex) {
            log.error("Failed to send notification | applicationId={} | status={} | error={}",
                    maskedId, event.getStatus(), ex.getMessage(), ex);

            // Wrap and rethrow so the consumer's retry/DLQ machinery engages
            throw new NotificationException(
                    "Email delivery failed for applicationId: " + maskedId, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Composes and dispatches a MIME multipart HTML email.
     *
     * @param to      recipient address (raw, from the event — never logged)
     * @param content generated subject and HTML body
     * @throws MessagingException propagated if MIME construction fails
     * @throws MailException      propagated if SMTP transport fails
     */
    private void sendHtmlEmail(String to, EmailContent content)
            throws MessagingException {

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        // MimeMessageHelper(message, multipart=true, charset)
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(content.subject());
        helper.setText(content.htmlBody(), /* html= */ true);

        mailSender.send(mimeMessage);
    }

    // -------------------------------------------------------------------------
    // Inner exception type
    // -------------------------------------------------------------------------

    /**
     * Unchecked exception signalling a non-transient or exhausted-retry
     * notification failure. Carrying the original cause preserves the full
     * stack trace for the DLQ handler / observability tooling.
     */
    public static class NotificationException extends RuntimeException {
        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
