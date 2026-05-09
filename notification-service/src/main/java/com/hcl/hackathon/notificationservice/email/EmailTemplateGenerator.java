package com.hcl.hackathon.notificationservice.email;

import com.hcl.hackathon.notificationservice.model.ApplicationStatusEvent;
import com.hcl.hackathon.notificationservice.util.DataMaskingUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds HTML email content for credit-card application status notifications.
 */
@Component
@RequiredArgsConstructor
public class EmailTemplateGenerator {

    private final DataMaskingUtil maskingUtil;

    public EmailContent generate(ApplicationStatusEvent event) {
        return switch (event.getStatus()) {
            case APPROVED -> buildApproved(event);
            case REJECTED -> buildRejected(event);
            case ERROR -> buildError(event);
        };
    }

    private EmailContent buildApproved(ApplicationStatusEvent event) {
        String maskedId = maskingUtil.maskApplicationId(event.getApplicationId());
        String subject = "Your credit card application has been approved (%s)".formatted(maskedId);

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Application approved</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 8px;
                              border-top: 5px solid #28a745; padding: 30px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <h1 style="color: #28a745; font-size: 24px;">Application approved</h1>
                    <p style="color: #333; font-size: 16px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555;">We are pleased to inform you that your credit card application
                       has been <strong style="color: #28a745;">approved</strong>.</p>
                    <div style="background: #eaffea; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0;
                                border-radius: 4px;">
                      <p style="margin: 0; color: #2d6a2d;"><strong>Reference ID:</strong> %s</p>
                      %s
                    </div>
                    <p style="color: #555;">Your card will be dispatched to your registered address
                       within <strong>5 to 7 business days</strong>.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">This is an automated message. Please do not reply
                       to this email. For assistance, contact
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
        String subject = "Update on your credit card application (%s)".formatted(maskedId);

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Application update</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 8px;
                              border-top: 5px solid #dc3545; padding: 30px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <h1 style="color: #dc3545; font-size: 24px;">Application status update</h1>
                    <p style="color: #333; font-size: 16px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555;">Thank you for your interest in our credit card. After review,
                       we regret that your application has been
                       <strong style="color: #dc3545;">declined</strong> at this time.</p>
                    <div style="background: #fff5f5; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0;
                                border-radius: 4px;">
                      <p style="margin: 0 0 8px; color: #a71d2a;"><strong>Reference ID:</strong> %s</p>
                      %s
                    </div>
                    <p style="color: #555;">You may re-apply after <strong>90 days</strong>. You may also
                       request a copy of your credit report from the bureau.</p>
                    <p style="color: #555;">If you believe this decision is incorrect, contact support with your
                       reference ID.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">This is an automated message. Please do not reply
                       to this email. Contact
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
        String subject = "Technical issue processing your application (%s)".formatted(maskedId);

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Technical issue</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 8px;
                              border-top: 5px solid #fd7e14; padding: 30px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <h1 style="color: #fd7e14; font-size: 24px;">Technical issue</h1>
                    <p style="color: #333; font-size: 16px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555;">We encountered a <strong>technical issue</strong> while processing
                       your credit card application. Our operations team has been notified.</p>
                    <div style="background: #fff8f0; border-left: 4px solid #fd7e14; padding: 15px; margin: 20px 0;
                                border-radius: 4px;">
                      <p style="margin: 0 0 8px; color: #7b3f00;"><strong>Reference ID:</strong> %s</p>
                      %s
                    </div>
                    <p style="color: #555;"><strong>No action is required from you at this time.</strong>
                       We will review your application and contact you within <strong>2 business days</strong>.</p>
                    <p style="color: #555;">We apologise for any inconvenience.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">This is an automated message. Please do not reply
                       to this email. Contact
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

    private String remarksBlock(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return "";
        }
        return "<p style=\"margin: 8px 0 0; color: #555;\"><strong>Remarks:</strong> %s</p>"
                .formatted(escapeHtml(remarks));
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    public record EmailContent(String subject, String htmlBody) {}
}
