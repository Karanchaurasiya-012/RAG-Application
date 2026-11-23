package com.processing_backend.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PdfValidationServiceTest {

	private PdfValidationService service;

	@BeforeEach
	void setUp() {
		AppProperties props = new AppProperties();
		props.getPdf().setMaxSizeMb(20);
		props.getPdf().setMaxPages(200);
		service = new PdfValidationService(props);
	}

	@Test
	void acceptsTextPdf() throws Exception {
		byte[] pdf = createTextPdf("Hello phase 4");
		MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/octet-stream", pdf);

		ValidatedPdf validated = service.validate(file);

		assertThat(validated.pageCount()).isEqualTo(1);
		assertThat(validated.contentType()).isEqualTo("application/pdf");
		assertThat(validated.checksumSha256()).hasSize(64);
		assertThat(validated.sanitizedFilename()).isEqualTo("sample.pdf");
	}

	@Test
	void rejectsNonPdfBytes() {
		MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", "not-a-pdf".getBytes());
		assertThatThrownBy(() -> service.validate(file))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("signature");
	}

	@Test
	void rejectsPathTraversalFilenameButKeepsBasename() throws Exception {
		byte[] pdf = createTextPdf("safe");
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"../../etc/passwd.pdf",
				"application/pdf",
				pdf
		);
		ValidatedPdf validated = service.validate(file);
		assertThat(validated.sanitizedFilename()).isEqualTo("passwd.pdf");
	}

	@Test
	void rejectsEmptyFile() {
		MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
		assertThatThrownBy(() -> service.validate(file))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void rejectsOversizedPayload() throws Exception {
		AppProperties props = new AppProperties();
		props.getPdf().setMaxSizeMb(1);
		props.getPdf().setMaxPages(200);
		PdfValidationService tight = new PdfValidationService(props);

		byte[] pdf = createTextPdf("tiny");
		byte[] padded = new byte[2 * 1024 * 1024];
		System.arraycopy(pdf, 0, padded, 0, pdf.length);
		MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", padded);

		assertThatThrownBy(() -> tight.validate(file))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("size");
	}

	private static byte[] createTextPdf(String text) throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(50, 700);
				content.showText(text);
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}
}
