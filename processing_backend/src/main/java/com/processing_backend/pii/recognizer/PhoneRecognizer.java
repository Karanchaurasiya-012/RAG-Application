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
public class PhoneRecognizer implements PiiRecognizer {

	// Indian mobiles (+91 / 0 optional) and generic international-ish digit groups.
	private static final Pattern PHONE = Pattern.compile(
			"(?<!\\d)(?:\\+?91[\\s-]?|0)?[6-9]\\d{9}(?!\\d)|(?<!\\d)\\+?[1-9]\\d{1,3}[\\s-]?\\d{6,12}(?!\\d)"
	);

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.PHONE_NUMBER;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		Matcher matcher = PHONE.matcher(page.text());
		while (matcher.find()) {
			String digits = matcher.group().replaceAll("\\D", "");
			if (digits.length() < 10 || digits.length() > 15) {
				continue;
			}
			double confidence = digits.length() == 10 || (digits.length() == 12 && digits.startsWith("91"))
					? 0.9
					: 0.75;
			spans.add(new PiiSpan(entityType(), page.pageNumber(), matcher.start(), matcher.end(), confidence));
		}
		return spans;
	}
}
