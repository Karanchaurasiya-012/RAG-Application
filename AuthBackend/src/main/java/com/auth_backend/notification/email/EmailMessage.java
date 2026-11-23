package com.auth_backend.notification.email;

public class EmailMessage {

	private final String to;
	private final String subject;
	private final String htmlBody;

	public EmailMessage(String to, String subject, String htmlBody) {
		this.to = to;
		this.subject = subject;
		this.htmlBody = htmlBody;
	}

	public String getTo() {
		return to;
	}

	public String getSubject() {
		return subject;
	}

	public String getHtmlBody() {
		return htmlBody;
	}
}
