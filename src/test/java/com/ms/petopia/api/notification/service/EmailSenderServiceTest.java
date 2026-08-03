package com.ms.petopia.api.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    @Mock
    JavaMailSender mailSender;

    EmailSenderService emailSenderService;

    @BeforeEach
    void setUp() {
        emailSenderService = new EmailSenderService(mailSender);
        // @Value 필드는 Spring이 주입하므로 테스트에서는 ReflectionTestUtils로 세팅
        ReflectionTestUtils.setField(emailSenderService, "from", "sender@example.com");
    }

    @Test
    @DisplayName("send() 호출 시 JavaMailSender.send()가 실행된다")
    void send_callsMailSender() {
        emailSenderService.send("to@example.com", "제목", "내용");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SimpleMailMessage에 from/to/subject/body가 올바르게 세팅된다")
    void send_setsMessageFields() {
        emailSenderService.send("to@example.com", "제목", "내용");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("to@example.com");
        assertThat(message.getSubject()).isEqualTo("제목");
        assertThat(message.getText()).isEqualTo("내용");
    }
}
