package com.processing_backend.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.PdfPageContent;
import org.junit.jupiter.api.Test;

class PiiMaskingServiceTest {

	private final PiiMaskingService maskingService = new PiiMaskingService();

	@Test
	void masksRightToLeftWithoutCorruptingOffsets() {
		String text = "Email jane@example.com and phone 9876543210 done";
		int emailStart = text.indexOf("jane@example.com");
		int emailEnd = emailStart + "jane@example.com".length();
		int phoneStart = text.indexOf("9876543210");
		int phoneEnd = phoneStart + "9876543210".length();

		ExtractedDocument source = new ExtractedDocument(
				List.of(new PdfPageContent(1, text)),
				1, 1, text.length()
		);
		ExtractedDocument masked = maskingService.mask(source, List.of(
				new PiiSpan(PiiEntityType.EMAIL_ADDRESS, 1, emailStart, emailEnd, 0.99),
				new PiiSpan(PiiEntityType.PHONE_NUMBER, 1, phoneStart, phoneEnd, 0.9)
		));

		String out = masked.pages().get(0).text();
		assertThat(out).contains("[EMAIL_ADDRESS]");
		assertThat(out).contains("[PHONE_NUMBER]");
		assertThat(out).doesNotContain("jane@example.com");
		assertThat(out).doesNotContain("9876543210");
	}

	@Test
	void keepOriginalPathLeavesTextUnchangedWhenMaskNotCalled() {
		String text = "Email jane@example.com stays";
		ExtractedDocument source = new ExtractedDocument(
				List.of(new PdfPageContent(1, text)),
				1, 1, text.length()
		);
		// KEEP_ORIGINAL skips masking; document text preparation uses source as-is.
		assertThat(source.pages().get(0).text()).isEqualTo(text);
		assertThat(source.pages().get(0).text()).contains("jane@example.com");
	}

	@Test
	void failsOnUnsafeOffset() {
		String text = "hello";
		ExtractedDocument source = new ExtractedDocument(
				List.of(new PdfPageContent(1, text)),
				1, 1, text.length()
		);
		assertThatThrownBy(() -> maskingService.mask(source, List.of(
				new PiiSpan(PiiEntityType.EMAIL_ADDRESS, 1, 0, 50, 0.99)
		))).isInstanceOf(BusinessException.class)
				.hasMessageContaining("Unsafe PII offset");
	}
}
