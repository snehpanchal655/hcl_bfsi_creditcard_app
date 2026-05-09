package com.creditcard.notification.email;

import com.creditcard.notification.model.ApplicationStatusEvent;
import com.creditcard.notification.util.DataMaskingUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responsible exclusively for producing email content (subject + HTML body).
 *
 * <p><b>SRP:</b> This class only builds email strings — it knows nothing about
 * SMTP transport, Kafka, or retry logic. It owns the "template" contract.
 *
 * <p>Templates are inline HTML strings to avoid classpath resource complexity
 * while still rendering rich, legible emails. A real-world extension would
 * swap these for Thymeleaf / Freemarker templates.
 */
@Component
@RequiredArgsConstructor
public class EmailTemplateGenerator {

    private final DataMaskingUtil maskingUtil;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds an {@link EmailContent} record appropriate for the event's status.
     *
     * @param event the fully-populated {@link ApplicationStatusEvent}
     * @return a value object carrying subject + HTML body
     * @throws IllegalArgumentException if the status is unrecognised
     */
    public EmailContent generate(ApplicationStatusEvent event) {
        return switch (event.getStatus()) {
            case APPROVED -> buildApproved(event);
            case REJECTED -> buildRejected(event);
            case ERROR    -> buildError(event);
        };
    }

    // -------------------------------------------------------------------------
    // Template builders
    // -------------------------------------------------------------------------

    private EmailContent buildApproved(ApplicationStatusEvent event) {
        String maskedId = maskingUtil.maskApplicationId(event.getApplicationId());
        String subject  = "🎉 Congratulations! Your Credit Card Application Has Been Approved (%s)".formatted(maskedId);

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Application Approved</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 8px;
                              border-top: 5px solid #28a745; padding: 30px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <h1 style="color: #28a745; font-size: 24px;">Application Approved ✅</h1>
                    <p style="color: #333; font-size: 16px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555;">We are thrilled to inform you that your credit card application
                       has been <strong style="color: #28a745;">APPROVED</strong>.</p>
                    <div style="background: #eaffea; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0;
                                border-radius: 4px;">
                      <p style="margin: 0; color: #2d6a2d;"><strong>Reference ID:</strong> %s</p>
                      %s
                    </div>
                    <p style="color: #555;">Your new credit card will be dispatched to your registered address
                       within <strong>5–7 business days</strong>. Please keep an eye on your mailbox.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">This is an automated message. Please do not reply
                       directly to this email. If you have any questions, contact our support at
                       <a href="mailto:support@creditcard.com">support@creditcard.com</a>.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(event.getCustomerName()),
                maskedId,
                remarksBlock(event.getRemarks())
        );

        return new EmailContent(subject, body);
    }

    private EmailContent buildRejected(ApplicationStatusEvent event) {
        String maskedId = maskingUtil.maskApplicationId(event.getApplicationId());
        String subject  = "Update on Your Credit Card Application (%s)".formatted(maskedId);

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Application Update</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 8px;
                              border-top: 5px solid #dc3545; padding: 30px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <h1 style="color: #dc3545; font-size: 24px;">Application Status Update</h1>
                    <p style="color: #333; font-size: 16px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555;">Thank you for applying for our credit card. After careful review,
                       we regret to inform you that your application has been
                       <strong style="color: #dc3545;">DECLINED</strong> at this time.</p>
                    <div style="background: #fff5f5; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0;
                                border-radius: 4px;">
                      <p style="margin: 0 0 8px; color: #a71d2a;"><strong>Reference ID:</strong> %s</p>
                      %s
                    </div>
                    <p style="color: #555;">You are welcome to re-apply after <strong>90 days</strong>.
                       In the meantime, you may request a free credit report to understand your credit profile.</p>
                    <p style="color: #555;">If you believe this decision was made in error, please contact our
                       support team with your Reference ID.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">This is an automated message. Please do not reply
                       directly to this email. Contact us at
                       <a href="mailto:support@creditcard.com">support@creditcard.com</a>.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(event.getCustomerName()),
                maskedId,
                remarksBlock(event.getRemarks())
        );

        return new EmailContent(subject, body);
    }

    private EmailContent buildError(ApplicationStatusEvent event) {
        String maskedId = maskingUtil.maskApplicationId(event.getApplicationId());
        String subject  = "Action Required: Technical Issue with Your Application (%s)".formatted(maskedId);

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Technical Issue</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 8px;
                              border-top: 5px solid #fd7e14; padding: 30px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <h1 style="color: #fd7e14; font-size: 24px;">Technical Issue Detected ⚠️</h1>
                    <p style="color: #333; font-size: 16px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555;">We encountered a <strong>technical issue</strong> while processing
                       your credit card application. Our engineering team has been automatically notified.</p>
                    <div style="background: #fff8f0; border-left: 4px solid #fd7e14; padding: 15px; margin: 20px 0;
                                border-radius: 4px;">
                      <p style="margin: 0 0 8px; color: #7b3f00;"><strong>Reference ID:</strong> %s</p>
                      %s
                    </div>
                    <p style="color: #555;"><strong>No action is required from your end.</strong>
                       Our team will review your application manually and contact you within
                       <strong>2 business days</strong>.</p>
                    <p style="color: #555;">We sincerely apologise for any inconvenience caused.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">This is an automated message. Please do not reply
                       directly to this email. Contact us at
                       <a href="mailto:support@creditcard.com">support@creditcard.com</a>.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(event.getCustomerName()),
                maskedId,
                remarksBlock(event.getRemarks())
        );

        return new EmailContent(subject, body);
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /** Renders an optional remarks block; returns empty string if no remarks. */
    private String remarksBlock(String remarks) {
        if (remarks == null || remarks.isBlank()) return "";
        return "<p style=\"margin: 8px 0 0; color: #555;\"><strong>Remarks:</strong> %s</p>"
                .formatted(escapeHtml(remarks));
    }

    /**
     * Minimal HTML escaping to prevent XSS if any field contains user-controlled text.
     * For production, prefer Apache Commons Text {@code StringEscapeUtils.escapeHtml4()}.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&#x27;");
    }

    // -------------------------------------------------------------------------
    // Value object
    // -------------------------------------------------------------------------

    /**
     * Immutable carrier for email subject and HTML body.
     * Using a record keeps the contract clear and allocation minimal.
     */
    public record EmailContent(String subject, String htmlBody) {}
}
