package com.auth_backend.notification.email;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test-profile email sink. Does not claim SMTP connectivity.
 */
@Service
@Primary
@Profile("test")
public class RecordingEmailService implements EmailService {

	private final List<EmailMessage> sentMessages = Collections.synchronizedList(new ArrayList<>());
	private final AtomicBoolean failNextSend = new AtomicBoolean(false);

	@Override
	public void sendVerificationEmail(String to, String verificationLink) {
		send(new EmailMessage(to, "Verify your email address", verificationLink));
	}

	@Override
	public void sendPasswordResetEmail(String to, String resetLink) {
		send(new EmailMessage(to, "Reset your password", resetLink));
	}

	public List<EmailMessage> getSentMessages() {
		return List.copyOf(sentMessages);
	}

	public void clear() {
		sentMessages.clear();
		failNextSend.set(false);
	}

	public void failNextSend() {
		failNextSend.set(true);
	}

	private void send(EmailMessage message) {
		if (failNextSend.compareAndSet(true, false)) {
			throw new EmailDeliveryException("Simulated SMTP failure.");
		}
		sentMessages.add(message);
	}
}
