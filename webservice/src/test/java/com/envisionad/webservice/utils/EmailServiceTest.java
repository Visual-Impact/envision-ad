package com.envisionad.webservice.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    private static final String FROM_EMAIL = "Envision Ad <no-reply@envision-ad.ca>";
    private static final String TO_EMAIL = "recipient@example.com";
    private static final String SUBJECT = "Test Subject";
    private static final String BODY = "Test email body content";

    @BeforeEach
    void setUp() {
        // Built by hand rather than @InjectMocks: the sender address is a constructor String,
        // which Mockito would pass as null.
        emailService = new EmailService(mailSender, FROM_EMAIL);
    }

    @Test
    void whenSendSimpleEmail_withValidParameters_thenSendEmailSuccessfully() {
        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, BODY);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage);
        assertEquals(FROM_EMAIL, capturedMessage.getFrom());
        assertArrayEquals(new String[]{TO_EMAIL}, capturedMessage.getTo());
        assertEquals(SUBJECT, capturedMessage.getSubject());
        assertEquals(BODY, capturedMessage.getText());
    }

    @Test
    void whenSendSimpleEmail_withMultilineBody_thenSendEmailWithCompleteBody() {
        // Arrange
        String multilineBody = "Line 1\nLine 2\nLine 3";

        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, multilineBody);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals(multilineBody, capturedMessage.getText());
        assertNotNull(capturedMessage.getText());
        assertTrue(capturedMessage.getText().contains("Line 1"));
        assertTrue(capturedMessage.getText().contains("Line 2"));
        assertTrue(capturedMessage.getText().contains("Line 3"));
    }

    @Test
    void whenSendSimpleEmail_withEmptyBody_thenSendEmailWithEmptyBody() {
        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, "");

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals("", capturedMessage.getText());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void whenSendSimpleEmail_withSpecialCharacters_thenSendEmailSuccessfully() {
        // Arrange
        String specialCharBody = "Special characters: @#$%^&*()_+-=[]{}|;':\",./<>?";

        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, specialCharBody);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals(specialCharBody, capturedMessage.getText());
    }

    @Test
    void whenSendSimpleEmail_withLongBody_thenSendEmailSuccessfully() {
        // Arrange
        String longBody = "A".repeat(5000);

        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, longBody);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals(longBody, capturedMessage.getText());
        assertNotNull(capturedMessage.getText());
        assertEquals(5000, capturedMessage.getText().length());
    }

    @Test
    void whenSendSimpleEmail_withHtmlContent_thenSendAsPlainText() {
        // Arrange
        String htmlBody = "<html><body><h1>Hello</h1><p>This is a test</p></body></html>";

        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, htmlBody);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        // Note: SimpleMailMessage sends plain text, so HTML tags will be sent as-is
        assertEquals(htmlBody, capturedMessage.getText());
    }

    @Test
    void whenSendSimpleEmail_withDifferentRecipients_thenSendToCorrectRecipient() {
        // Arrange
        String recipient1 = "user1@example.com";
        String recipient2 = "user2@example.com";

        // Act
        emailService.sendSimpleEmail(recipient1, SUBJECT, BODY);
        emailService.sendSimpleEmail(recipient2, SUBJECT, BODY);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(messageCaptor.capture());

        SimpleMailMessage firstMessage = messageCaptor.getAllValues().get(0);
        SimpleMailMessage secondMessage = messageCaptor.getAllValues().get(1);

        assertArrayEquals(new String[]{recipient1}, firstMessage.getTo());
        assertArrayEquals(new String[]{recipient2}, secondMessage.getTo());
    }

    @Test
    void whenSendSimpleEmail_withDifferentSubjects_thenSendWithCorrectSubject() {
        // Arrange
        String subject1 = "Welcome Email";
        String subject2 = "Password Reset";

        // Act
        emailService.sendSimpleEmail(TO_EMAIL, subject1, BODY);
        emailService.sendSimpleEmail(TO_EMAIL, subject2, BODY);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(messageCaptor.capture());

        SimpleMailMessage firstMessage = messageCaptor.getAllValues().get(0);
        SimpleMailMessage secondMessage = messageCaptor.getAllValues().get(1);

        assertEquals(subject1, firstMessage.getSubject());
        assertEquals(subject2, secondMessage.getSubject());
    }

    @Test
    void whenSendSimpleEmail_calledMultipleTimes_thenSendMultipleEmails() {
        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, BODY);
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, BODY);
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, BODY);

        // Assert
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    void whenSendSimpleEmail_withMailSenderThrowingException_thenExceptionPropagates() {
        // Arrange
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, BODY));

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void whenSendSimpleEmail_verifyFromAddressIsAlwaysConstant() {
        // Act
        emailService.sendSimpleEmail("user1@example.com", "Subject 1", "Body 1");
        emailService.sendSimpleEmail("user2@example.com", "Subject 2", "Body 2");
        emailService.sendSimpleEmail("user3@example.com", "Subject 3", "Body 3");

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(3)).send(messageCaptor.capture());

        // All emails should have the same FROM address
        for (SimpleMailMessage message : messageCaptor.getAllValues()) {
            assertEquals(FROM_EMAIL, message.getFrom());
        }
    }

    @Test
    void whenSendSimpleEmail_withUnicodeCharacters_thenSendSuccessfully() {
        // Arrange
        String unicodeBody = "Hello 世界! Bonjour! Привет! مرحبا! 🎉🎊";

        // Act
        emailService.sendSimpleEmail(TO_EMAIL, "Unicode Test", unicodeBody);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals(unicodeBody, capturedMessage.getText());
    }

    @Test
    void whenSendSimpleEmail_withEmailContainingPlusSign_thenSendSuccessfully() {
        // Arrange
        String emailWithPlus = "user+test@example.com";

        // Act
        emailService.sendSimpleEmail(emailWithPlus, SUBJECT, BODY);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertArrayEquals(new String[]{emailWithPlus}, capturedMessage.getTo());
    }

    @Test
    void whenSendSimpleEmail_withEmptySubject_thenSendEmailWithEmptySubject() {
        // Act
        emailService.sendSimpleEmail(TO_EMAIL, "", BODY);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals("", capturedMessage.getSubject());
    }

    @Test
    void whenSendSimpleEmail_verifyMessageStructureIsComplete() {
        // Act
        emailService.sendSimpleEmail(TO_EMAIL, SUBJECT, BODY);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();

        // Verify all fields are set
        assertNotNull(capturedMessage.getFrom(), "From address should not be null");
        assertNotNull(capturedMessage.getTo(), "To address should not be null");
        assertNotNull(capturedMessage.getSubject(), "Subject should not be null");
        assertNotNull(capturedMessage.getText(), "Body should not be null");

        assertEquals(1, capturedMessage.getTo().length, "Should have exactly one recipient");
    }
}

