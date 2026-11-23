package com.processing_backend.pii.recognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.processing_backend.pdf.PdfPageContent;
import com.processing_backend.pii.PiiEntityType;
import com.processing_backend.pii.PiiRecognizer;
import com.processing_backend.pii.PiiSpan;
import org.springframework.stereotype.Component;

@Component
public class BankAccountRecognizer implements PiiRecognizer {

	private static final Pattern ACCOUNT = Pattern.compile("(?<!\\d)\\d{9,18}(?!\\d)");

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.BANK_ACCOUNT;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		String text = page.text();
		Matcher matcher = ACCOUNT.matcher(text);
		while (matcher.find()) {
			String digits = matcher.group();
			// Skip likely phones / aadhaar-length without bank context.
			if (digits.length() == 10 || digits.length() == 12) {
				continue;
			}
			if (!hasBankContext(text, matcher.start(), matcher.end())) {
				continue;
			}
			spans.add(new PiiSpan(entityType(), page.pageNumber(), matcher.start(), matcher.end(), 0.7));
		}
		return spans;
	}

	private static boolean hasBankContext(String text, int start, int end) {
		int from = Math.max(0, start - 30);
		int to = Math.min(text.length(), end + 30);
		String window = text.substring(from, to).toLowerCase();
		return window.contains("account")
				|| window.contains("a/c")
				|| window.contains("bank")
				|| window.contains("ifsc");
	}
}
