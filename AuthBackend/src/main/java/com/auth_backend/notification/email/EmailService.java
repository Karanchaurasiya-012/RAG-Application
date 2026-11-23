package com.auth_backend.notification.email;

public interface EmailService {

	void sendVerificationEmail(String to, String verificationLink);

	void sendPasswordResetEmail(String to, String resetLink);
}
