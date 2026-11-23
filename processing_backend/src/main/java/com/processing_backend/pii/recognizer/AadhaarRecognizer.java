package com.processing_backend.pii.recognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.processing_backend.pdf.PdfPageContent;
import com.processing_backend.pii.PiiEntityType;
import com.processing_backend.pii.PiiRecognizer;
import com.processing_backend.pii.PiiSpan;
import com.processing_backend.pii.checksum.Verhoeff;
import org.springframework.stereotype.Component;

@Component
public class AadhaarRecognizer implements PiiRecognizer {

	private static final Pattern AADHAAR = Pattern.compile("(?<!\\d)[2-9]\\d{3}[\\s-]?\\d{4}[\\s-]?\\d{4}(?!\\d)");

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.AADHAAR;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		Matcher matcher = AADHAAR.matcher(page.text());
		while (matcher.find()) {
			String digits = matcher.group().replaceAll("\\D", "");
			if (digits.length() != 12) {
				continue;
			}
			if (!Verhoeff.isValid(digits)) {
				continue;
			}
			spans.add(new PiiSpan(entityType(), page.pageNumber(), matcher.start(), matcher.end(), 0.95));
		}
		return spans;
	}
}
