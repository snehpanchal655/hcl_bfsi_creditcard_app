package com.hcl.hackathon.notificationservice.service;

import com.hcl.hackathon.notificationservice.email.EmailTemplateGenerator;
import com.hcl.hackathon.notificationservice.email.EmailTemplateGenerator.EmailContent;
import com.hcl.hackathon.notificationservice.kafka.KafkaMessageContext;
import com.hcl.hackathon.notificationservice.model.ApplicationStatusEvent;
import com.hcl.hackathon.notificationservice.model.ApplicationStatusEvent.ApplicationStatus;
import com.hcl.hackathon.notificationservice.service.NotificationService.NotificationException;
import com.hcl.hackathon.notificationservice.util.DataMaskingUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    private static final KafkaMessageContext KAFKA_CTX = new KafkaMessageContext("card.status", 0, 1L);

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateGenerator emailTemplateGenerator;

    @Mock
    private DataMaskingUtil maskingUtil;

    @Mock
    private NotificationLogService notificationLogService;

    @InjectMocks
    private NotificationService notificationService;

    private MimeMessage mockMimeMessage;

    private static final String MASKED_ID = "APP-XXXX-0042";
    private static final String MASKED_EMAIL = "j***@e*****.com";
    private static final String FROM_ADDRESS = "noreply@creditcard.com";

    @BeforeEach
    void setUp() throws MessagingException {
        ReflectionTestUtils.setField(notificationService, "fromAddress", FROM_ADDRESS);

        mockMimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        when(maskingUtil.maskApplicationId(any())).thenReturn(MASKED_ID);
        when(maskingUtil.maskEmail(any())).thenReturn(MASKED_EMAIL);
    }

    @Nested
    @DisplayName("when status is APPROVED")
    class ApprovedEvents {

        @Test
        @DisplayName("should call mailSender.send() exactly once")
        void shouldSendExactlyOneEmail() {
            ApplicationStatusEvent event = approvedEvent();
            EmailContent content = new EmailContent("Approved Subject", "<html>approved</html>");
            when(emailTemplateGenerator.generate(event)).thenReturn(content);

            notificationService.processNotification(event, KAFKA_CTX);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
            verify(notificationLogService).recordSuccess(eq(event), eq(KAFKA_CTX), any());
        }

        @Test
        @DisplayName("should delegate email generation to EmailTemplateGenerator")
        void shouldDelegateToTemplateGenerator() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event)).thenReturn(new EmailContent("Subject", "<html/>"));

            notificationService.processNotification(event, KAFKA_CTX);

            verify(emailTemplateGenerator, times(1)).generate(event);
        }

        @Test
        @DisplayName("should mask applicationId before logging (maskingUtil called)")
        void shouldMaskApplicationId() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event)).thenReturn(new EmailContent("S", "<h/>"));

            notificationService.processNotification(event, KAFKA_CTX);

            verify(maskingUtil, times(1)).maskApplicationId(event.getApplicationId());
        }
    }

    @Nested
    @DisplayName("when status is REJECTED")
    class RejectedEvents {

        @Test
        @DisplayName("should call mailSender.send() exactly once")
        void shouldSendExactlyOneEmail() {
            ApplicationStatusEvent event = rejectedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Rejected Subject", "<html>rejected</html>"));

            notificationService.processNotification(event, KAFKA_CTX);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
            verify(notificationLogService).recordSuccess(eq(event), eq(KAFKA_CTX), any());
        }
    }

    @Nested
    @DisplayName("when status is ERROR")
    class ErrorEvents {

        @Test
        @DisplayName("should call mailSender.send() exactly once")
        void shouldSendExactlyOneEmail() {
            ApplicationStatusEvent event = errorEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Error Subject", "<html>error</html>"));

            notificationService.processNotification(event, KAFKA_CTX);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
            verify(notificationLogService).recordSuccess(eq(event), eq(KAFKA_CTX), any());
        }
    }

    @Nested
    @DisplayName("when mail transport fails")
    class MailTransportFailures {

        @Test
        @DisplayName("should throw NotificationException wrapping the MailException")
        void shouldWrapAndRethrowMailException() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event)).thenReturn(new EmailContent("Subject", "<html/>"));
            doThrow(new MailSendException("SMTP connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> notificationService.processNotification(event, KAFKA_CTX))
                    .isInstanceOf(NotificationException.class)
                    .hasMessageContaining(MASKED_ID)
                    .hasCauseInstanceOf(MailSendException.class);

            verify(notificationLogService).recordFailure(eq(event), eq(KAFKA_CTX), any(), anyString());
        }

        @Test
        @DisplayName("should NOT swallow exceptions (send() must not be called again)")
        void shouldNotRetryInternally() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event)).thenReturn(new EmailContent("Subject", "<html/>"));
            doThrow(new MailSendException("timeout"))
                    .when(mailSender).send(any(MimeMessage.class));

            try {
                notificationService.processNotification(event, KAFKA_CTX);
            } catch (NotificationException ignored) {
                // expected
            }

            verify(mailSender, times(1)).send(any(MimeMessage.class));
            verify(notificationLogService).recordFailure(eq(event), eq(KAFKA_CTX), any(), anyString());
        }
    }

    @Nested
    @DisplayName("MimeMessage construction")
    class MimeMessageConstruction {

        @Test
        @DisplayName("should create a MimeMessage via mailSender.createMimeMessage()")
        void shouldCreateMimeMessage() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event)).thenReturn(new EmailContent("Subj", "<p>body</p>"));

            notificationService.processNotification(event, KAFKA_CTX);

            verify(mailSender, times(1)).createMimeMessage();
        }

        @Test
        @DisplayName("should pass the MimeMessage returned by createMimeMessage() to send()")
        void shouldPassSameMimeMessageToSend() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event)).thenReturn(new EmailContent("Subj", "<p>body</p>"));

            notificationService.processNotification(event, KAFKA_CTX);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());

            assertThat(captor.getValue()).isSameAs(mockMimeMessage);
        }
    }

    private ApplicationStatusEvent approvedEvent() {
        return ApplicationStatusEvent.builder()
                .applicationId("APP-2024-00042")
                .email("john.doe@example.com")
                .customerName("John Doe")
                .status(ApplicationStatus.APPROVED)
                .remarks(null)
                .build();
    }

    private ApplicationStatusEvent rejectedEvent() {
        return ApplicationStatusEvent.builder()
                .applicationId("APP-2024-00099")
                .email("jane.smith@example.com")
                .customerName("Jane Smith")
                .status(ApplicationStatus.REJECTED)
                .remarks("Insufficient credit history")
                .build();
    }

    private ApplicationStatusEvent errorEvent() {
        return ApplicationStatusEvent.builder()
                .applicationId("APP-2024-00077")
                .email("error.user@example.com")
                .customerName("Error User")
                .status(ApplicationStatus.ERROR)
                .remarks("Downstream bureau service timeout")
                .build();
    }
}
