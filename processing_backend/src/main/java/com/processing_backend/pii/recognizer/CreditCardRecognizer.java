package com.processing_backend.pii.recognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.processing_backend.pdf.PdfPageContent;
import com.processing_backend.pii.PiiEntityType;
import com.processing_backend.pii.PiiRecognizer;
import com.processing_backend.pii.PiiSpan;
import com.processing_backend.pii.checksum.Luhn;
import org.springframework.stereotype.Component;

@Component
public class CreditCardRecognizer implements PiiRecognizer {

	private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)");

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.CREDIT_CARD;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		Matcher matcher = CARD.matcher(page.text());
		while (matcher.find()) {
			String digits = matcher.group().replaceAll("\\D", "");
			if (digits.length() < 13 || digits.length() > 19) {
				continue;
			}
			if (!Luhn.isValid(digits)) {
				continue;
			}
			spans.add(new PiiSpan(entityType(), page.pageNumber(), matcher.start(), matcher.end(), 0.93));
		}
		return spans;
	}
}
