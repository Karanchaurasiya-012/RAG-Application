package com.processing_backend.pii;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.PdfPageContent;
import com.processing_backend.pii.recognizer.AadhaarRecognizer;
import com.processing_backend.pii.recognizer.BankAccountRecognizer;
import com.processing_backend.pii.recognizer.CreditCardRecognizer;
import com.processing_backend.pii.recognizer.EmailRecognizer;
import com.processing_backend.pii.recognizer.IfscRecognizer;
import com.processing_backend.pii.recognizer.PanRecognizer;
import com.processing_backend.pii.recognizer.PassportRecognizer;
import com.processing_backend.pii.recognizer.PhoneRecognizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PiiDetectionServiceTest {

	private PiiDetectionService service;

	@BeforeEach
	void setUp() {
		service = new PiiDetectionService(List.of(
				new EmailRecognizer(),
				new PhoneRecognizer(),
				new PanRecognizer(),
				new AadhaarRecognizer(),
				new CreditCardRecognizer(),
				new PassportRecognizer(),
				new IfscRecognizer(),
				new BankAccountRecognizer()
		));
	}

	@Test
	void detectsEmailPhonePanIfscWithoutReturningRawValuesInSummary() {
		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(
						1,
						"Contact jane.doe@example.com or +91 9876543210. PAN ABCPE1234F IFSC HDFC0001234."
				)),
				1,
				1,
				100
		);

		PiiDetectionResult result = service.detect(doc);

		assertThat(result.hasPii()).isTrue();
		assertThat(result.summary()).extracting(PiiSummaryItem::type)
				.contains(PiiEntityType.EMAIL_ADDRESS, PiiEntityType.PHONE_NUMBER, PiiEntityType.PAN, PiiEntityType.IFSC);
		assertThat(result.summary().toString()).doesNotContain("jane.doe@example.com");
		assertThat(result.summary().toString()).doesNotContain("9876543210");
		assertThat(result.summary().toString()).doesNotContain("ABCPE1234F");
	}

	@Test
	void validatesAadhaarWithVerhoeffAndCardWithLuhn() {
		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(
						1,
						"Aadhaar 2345 6789 0124 card 4111 1111 1111 1111"
				)),
				1,
				1,
				80
		);

		PiiDetectionResult result = service.detect(doc);

		assertThat(result.summary()).extracting(PiiSummaryItem::type)
				.contains(PiiEntityType.AADHAAR, PiiEntityType.CREDIT_CARD);
	}

	@Test
	void rejectsInvalidAadhaarChecksum() {
		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(1, "Aadhaar 2345 6789 0123")),
				1,
				1,
				30
		);
		PiiDetectionResult result = service.detect(doc);
		assertThat(result.summary()).extracting(PiiSummaryItem::type)
				.doesNotContain(PiiEntityType.AADHAAR);
	}

	@Test
	void resolvesOverlappingSpansPreferringHigherConfidence() {
		List<PiiSpan> resolved = PiiDetectionService.resolveOverlaps(List.of(
				new PiiSpan(PiiEntityType.PHONE_NUMBER, 1, 0, 10, 0.7),
				new PiiSpan(PiiEntityType.AADHAAR, 1, 5, 17, 0.95)
		));
		assertThat(resolved).hasSize(1);
		assertThat(resolved.get(0).entityType()).isEqualTo(PiiEntityType.AADHAAR);
	}

	@Test
	void passportRequiresContext() {
		ExtractedDocument without = new ExtractedDocument(
				List.of(new PdfPageContent(1, "Code A1234567 appears here")),
				1, 1, 20
		);
		ExtractedDocument with = new ExtractedDocument(
				List.of(new PdfPageContent(1, "Passport number A1234567 issued")),
				1, 1, 30
		);
		assertThat(service.detect(without).summary()).extracting(PiiSummaryItem::type)
				.doesNotContain(PiiEntityType.PASSPORT_NUMBER);
		assertThat(service.detect(with).summary()).extracting(PiiSummaryItem::type)
				.contains(PiiEntityType.PASSPORT_NUMBER);
	}

	@Test
	void cleanTextHasNoPii() {
		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(1, "Quarterly revenue grew by twelve percent.")),
				1, 1, 40
		);
		assertThat(service.detect(doc).hasPii()).isFalse();
	}
}
