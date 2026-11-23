package com.auth_backend.notification.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.auth_backend.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
@Profile("!test")
public class SmtpEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

	private final JavaMailSender mailSender;
	private final AppProperties appProperties;

	public SmtpEmailService(JavaMailSender mailSender, AppProperties appProperties) {
		this.mailSender = mailSender;
		this.appProperties = appProperties;
	}

	@Override
	public void sendVerificationEmail(String to, String verificationLink) {
		String html = loadTemplate("notification/email/template/verification-email.html")
				.replace("{{verificationLink}}", verificationLink);
		send(new EmailMessage(to, "Verify your email address", html));
	}

	@Override
	public void sendPasswordResetEmail(String to, String resetLink) {
		String html = loadTemplate("notification/email/template/password-reset.html")
				.replace("{{resetLink}}", resetLink);
		send(new EmailMessage(to, "Reset your password", html));
	}

	private void send(EmailMessage message) {
		try {
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
			helper.setTo(message.getTo());
			helper.setSubject(message.getSubject());
			helper.setText(message.getHtmlBody(), true);
			helper.setFrom(resolveFromAddress());
			mailSender.send(mimeMessage);
			log.info("Email sent to={} subject={}", message.getTo(), message.getSubject());
		}
		catch (MessagingException | MailException ex) {
			log.error("SMTP delivery failed for to={}", message.getTo());
			throw new EmailDeliveryException("Failed to send email via SMTP.", ex);
		}
	}

	private String resolveFromAddress() {
		String from = appProperties.getEmail().getFrom();
		if (from != null && !from.isBlank()) {
			return from;
		}
		throw new EmailDeliveryException("SMTP from address is not configured.");
	}

	private String loadTemplate(String classpathLocation) {
		try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
			return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new EmailDeliveryException("Email template missing: " + classpathLocation, ex);
		}
	}
}
