package com.creditcard.notification.service;

import com.creditcard.notification.email.EmailTemplateGenerator;
import com.creditcard.notification.email.EmailTemplateGenerator.EmailContent;
import com.creditcard.notification.model.ApplicationStatusEvent;
import com.creditcard.notification.model.ApplicationStatusEvent.ApplicationStatus;
import com.creditcard.notification.service.NotificationService.NotificationException;
import com.creditcard.notification.util.DataMaskingUtil;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService}.
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>Verifies that {@link JavaMailSender#send(MimeMessage)} is called
 *       <em>exactly once</em> on a successful event.</li>
 *   <li>Verifies that a {@link NotificationException} is raised (never swallowed)
 *       when the mail transport fails.</li>
 *   <li>Verifies correct delegation to {@link EmailTemplateGenerator}.</li>
 *   <li>Verifies status-specific routing (APPROVED / REJECTED / ERROR).</li>
 * </ul>
 *
 * <h3>Design decisions</h3>
 * {@code @ExtendWith(MockitoExtension.class)} initialises mocks without a Spring
 * context, keeping tests fast (&lt;200 ms). {@link MimeMessage} is a concrete
 * class with a complex constructor, so we use {@code mock(MimeMessage.class)}
 * returned by {@link JavaMailSender#createMimeMessage()}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    // -------------------------------------------------------------------------
    // Mocks
    // -------------------------------------------------------------------------

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateGenerator emailTemplateGenerator;

    @Mock
    private DataMaskingUtil maskingUtil;

    @InjectMocks
    private NotificationService notificationService;

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private MimeMessage mockMimeMessage;

    private static final String MASKED_ID    = "APP-XXXX-0042";
    private static final String MASKED_EMAIL = "j***@e*****.com";
    private static final String FROM_ADDRESS = "noreply@creditcard.com";

    @BeforeEach
    void setUp() throws MessagingException {
        // Inject the @Value field that Spring normally populates
        ReflectionTestUtils.setField(notificationService, "fromAddress", FROM_ADDRESS);

        // JavaMailSender.createMimeMessage() must return a mockable MimeMessage
        mockMimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        // Default masking stubs — override per test where needed
        when(maskingUtil.maskApplicationId(any())).thenReturn(MASKED_ID);
        when(maskingUtil.maskEmail(any())).thenReturn(MASKED_EMAIL);
    }

    // =========================================================================
    // Nested: APPROVED
    // =========================================================================

    @Nested
    @DisplayName("when status is APPROVED")
    class ApprovedEvents {

        @Test
        @DisplayName("should call mailSender.send() exactly once")
        void shouldSendExactlyOneEmail() {
            // Arrange
            ApplicationStatusEvent event = approvedEvent();
            EmailContent content = new EmailContent("Approved Subject", "<html>approved</html>");
            when(emailTemplateGenerator.generate(event)).thenReturn(content);

            // Act
            notificationService.processNotification(event);

            // Assert — THE core requirement: send() called exactly once
            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should delegate email generation to EmailTemplateGenerator")
        void shouldDelegateToTemplateGenerator() {
            // Arrange
            ApplicationStatusEvent event = approvedEvent();
            EmailContent content = new EmailContent("Subject", "<html/>");
            when(emailTemplateGenerator.generate(event)).thenReturn(content);

            // Act
            notificationService.processNotification(event);

            // Assert
            verify(emailTemplateGenerator, times(1)).generate(event);
        }

        @Test
        @DisplayName("should mask applicationId before logging (maskingUtil called)")
        void shouldMaskApplicationId() {
            // Arrange
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("S", "<h/>"));

            // Act
            notificationService.processNotification(event);

            // Assert
            verify(maskingUtil, atLeastOnce()).maskApplicationId(event.getApplicationId());
        }
    }

    // =========================================================================
    // Nested: REJECTED
    // =========================================================================

    @Nested
    @DisplayName("when status is REJECTED")
    class RejectedEvents {

        @Test
        @DisplayName("should call mailSender.send() exactly once")
        void shouldSendExactlyOneEmail() {
            ApplicationStatusEvent event = rejectedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Rejected Subject", "<html>rejected</html>"));

            notificationService.processNotification(event);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    // =========================================================================
    // Nested: ERROR
    // =========================================================================

    @Nested
    @DisplayName("when status is ERROR")
    class ErrorEvents {

        @Test
        @DisplayName("should call mailSender.send() exactly once")
        void shouldSendExactlyOneEmail() {
            ApplicationStatusEvent event = errorEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Error Subject", "<html>error</html>"));

            notificationService.processNotification(event);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    // =========================================================================
    // Nested: Failure / Resilience
    // =========================================================================

    @Nested
    @DisplayName("when mail transport fails")
    class MailTransportFailures {

        @Test
        @DisplayName("should throw NotificationException wrapping the MailException")
        void shouldWrapAndRethrowMailException() {
            // Arrange
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Subject", "<html/>"));
            doThrow(new MailSendException("SMTP connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));

            // Act + Assert
            assertThatThrownBy(() -> notificationService.processNotification(event))
                    .isInstanceOf(NotificationException.class)
                    .hasMessageContaining(MASKED_ID)
                    .hasCauseInstanceOf(MailSendException.class);
        }

        @Test
        @DisplayName("should NOT swallow exceptions (send() must not be called again)")
        void shouldNotRetryInternally() {
            // Arrange
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Subject", "<html/>"));
            doThrow(new MailSendException("timeout"))
                    .when(mailSender).send(any(MimeMessage.class));

            // Act — swallow the expected exception to continue assertions
            try {
                notificationService.processNotification(event);
            } catch (NotificationException ignored) {
                // expected
            }

            // The service must NOT retry internally; retry is Kafka infrastructure's job
            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    // =========================================================================
    // Nested: MimeMessage construction
    // =========================================================================

    @Nested
    @DisplayName("MimeMessage construction")
    class MimeMessageConstruction {

        @Test
        @DisplayName("should create a MimeMessage via mailSender.createMimeMessage()")
        void shouldCreateMimeMessage() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Subj", "<p>body</p>"));

            notificationService.processNotification(event);

            verify(mailSender, times(1)).createMimeMessage();
        }

        @Test
        @DisplayName("should pass the MimeMessage returned by createMimeMessage() to send()")
        void shouldPassSameMimeMessageToSend() {
            ApplicationStatusEvent event = approvedEvent();
            when(emailTemplateGenerator.generate(event))
                    .thenReturn(new EmailContent("Subj", "<p>body</p>"));

            notificationService.processNotification(event);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());

            // The MimeMessage passed to send() must be the one createMimeMessage() returned
            assertThat(captor.getValue()).isSameAs(mockMimeMessage);
        }
    }

    // =========================================================================
    // Factory helpers
    // =========================================================================

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
