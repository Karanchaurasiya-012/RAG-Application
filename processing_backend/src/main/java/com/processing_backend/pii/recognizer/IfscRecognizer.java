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
public class IfscRecognizer implements PiiRecognizer {

	private static final Pattern IFSC = Pattern.compile("\\b[A-Z]{4}0[A-Z0-9]{6}\\b", Pattern.CASE_INSENSITIVE);

	@Override
	public PiiEntityType entityType() {
		return PiiEntityType.IFSC;
	}

	@Override
	public List<PiiSpan> detect(PdfPageContent page) {
		List<PiiSpan> spans = new ArrayList<>();
		Matcher matcher = IFSC.matcher(page.text());
		while (matcher.find()) {
			spans.add(new PiiSpan(entityType(), page.pageNumber(), matcher.start(), matcher.end(), 0.9));
		}
		return spans;
	}
}
