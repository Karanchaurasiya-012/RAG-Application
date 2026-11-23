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
public class EmailRecognizer implements PiiRecognizer {

	private static final Pattern EMAIL = Pattern.compile(
			"\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
	);

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.EMAIL_ADDRESS;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		Matcher matcher = EMAIL.matcher(page.text());
		while (matcher.find()) {
			spans.add(new PiiSpan(
					entityType(),
					page.pageNumber(),
					matcher.start(),
					matcher.end(),
					0.99
			));
		}
		return spans;
	}
}
