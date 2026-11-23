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
public class PassportRecognizer implements PiiRecognizer {

	// Indian passport: 1 letter + 7 digits (common form). Lower confidence without MRZ context.
	private static final Pattern PASSPORT = Pattern.compile("\\b[A-Z][0-9]{7}\\b", Pattern.CASE_INSENSITIVE);

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.PASSPORT_NUMBER;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		String text = page.text();
		Matcher matcher = PASSPORT.matcher(text);
		while (matcher.find()) {
			double confidence = hasPassportContext(text, matcher.start(), matcher.end()) ? 0.85 : 0.65;
			if (confidence < 0.7) {
				// Avoid high false positives on random letter+digits without context.
				continue;
			}
			spans.add(new PiiSpan(entityType(), page.pageNumber(), matcher.start(), matcher.end(), confidence));
		}
		return spans;
	}

	private static boolean hasPassportContext(String text, int start, int end) {
		int from = Math.max(0, start - 24);
		int to = Math.min(text.length(), end + 24);
		String window = text.substring(from, to).toLowerCase();
		return window.contains("passport") || window.contains("travel document");
	}
}
