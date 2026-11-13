package com.hotelai.notification.delivery;

import com.hotelai.notification.domain.NotificationChannel;
import java.util.Properties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hotel.notification.email.mode", havingValue = "smtp")
public class SmtpEmailAdapter implements DeliveryAdapter {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailAdapter.class);

    private final JavaMailSender sender;
    private final JavaMailSender fallbackSender;
    private final String from;
    private final boolean fallbackToMailpit;

    public SmtpEmailAdapter(
            JavaMailSender sender,
            @Value("${hotel.notification.email.from}") String from,
            @Value("${hotel.notification.email.fallback-to-mailpit:false}") boolean fallbackToMailpit,
            @Value("${hotel.notification.email.fallback-host:mailpit}") String fallbackHost,
            @Value("${hotel.notification.email.fallback-port:1025}") int fallbackPort,
            @Value("${hotel.notification.email.fallback-auth:false}") boolean fallbackAuth,
            @Value("${hotel.notification.email.fallback-starttls:false}") boolean fallbackStartTls,
            @Value("${hotel.notification.email.fallback-connection-timeout-ms:3000}") int fallbackConnectionTimeout,
            @Value("${hotel.notification.email.fallback-timeout-ms:5000}") int fallbackTimeout,
            @Value("${hotel.notification.email.fallback-write-timeout-ms:5000}") int fallbackWriteTimeout) {
        this.sender = sender;
        this.from = from;
        this.fallbackToMailpit = fallbackToMailpit;
        this.fallbackSender = fallbackToMailpit
                ? createSender(fallbackHost, fallbackPort, null, null, fallbackAuth, fallbackStartTls,
                        fallbackConnectionTimeout, fallbackTimeout, fallbackWriteTimeout)
                : null;
    }

    @Override
    public NotificationChannel channel() { return NotificationChannel.EMAIL; }

    @Override
    public DeliveryResult send(DeliveryRequest request) {
        SimpleMailMessage message = message(request, from);
        try {
            sender.send(message);
            return new DeliveryResult("smtp-" + UUID.randomUUID());
        } catch (MailException | IllegalStateException primaryFailure) {
            if (!fallbackToMailpit) throw primaryFailure;
            try {
                fallbackSender.send(message(request, from));
                log.warn("Primary SMTP delivery failed; captured email in Mailpit correlationId={}",
                        request.correlationId(), primaryFailure);
                return new DeliveryResult("mailpit-fallback-" + UUID.randomUUID());
            } catch (RuntimeException fallbackFailure) {
                primaryFailure.addSuppressed(fallbackFailure);
                throw primaryFailure;
            }
        }
    }

    private static SimpleMailMessage message(DeliveryRequest request, String from) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.recipient());
        message.setSubject(request.subject());
        message.setText(request.content());
        return message;
    }

    private static JavaMailSender createSender(
            String host,
            int port,
            String username,
            String password,
            boolean auth,
            boolean startTls,
            int connectionTimeout,
            int timeout,
            int writeTimeout) {
        JavaMailSenderImpl result = new JavaMailSenderImpl();
        result.setHost(host);
        result.setPort(port);
        if (username != null && !username.isBlank()) result.setUsername(username);
        if (password != null && !password.isBlank()) result.setPassword(password);
        Properties properties = result.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(auth));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.put("mail.smtp.connectiontimeout", Integer.toString(connectionTimeout));
        properties.put("mail.smtp.timeout", Integer.toString(timeout));
        properties.put("mail.smtp.writetimeout", Integer.toString(writeTimeout));
        return result;
    }
}
