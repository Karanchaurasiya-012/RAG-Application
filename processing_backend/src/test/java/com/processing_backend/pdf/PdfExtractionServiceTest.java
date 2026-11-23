package com.processing_backend.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;

import com.processing_backend.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfExtractionServiceTest {

	private final PdfExtractionService service = new PdfExtractionService();

	@Test
	void extractsPageByPageWithNumbers() throws Exception {
		byte[] pdf = createMultiPagePdf("Alpha page", "Beta page");

		ExtractedDocument extracted = service.extractFromBytes(pdf);

		assertThat(extracted.pageCount()).isEqualTo(2);
		assertThat(extracted.nonEmptyPageCount()).isEqualTo(2);
		assertThat(extracted.pages()).hasSize(2);
		assertThat(extracted.pages().get(0).pageNumber()).isEqualTo(1);
		assertThat(extracted.pages().get(0).text()).contains("Alpha page");
		assertThat(extracted.pages().get(1).pageNumber()).isEqualTo(2);
		assertThat(extracted.pages().get(1).text()).contains("Beta page");
	}

	@Test
	void keepsEmptyPagesAligned() throws Exception {
		byte[] pdf = createPdfWithBlankSecondPage("Only first");

		ExtractedDocument extracted = service.extractFromBytes(pdf);

		assertThat(extracted.pageCount()).isEqualTo(2);
		assertThat(extracted.nonEmptyPageCount()).isEqualTo(1);
		assertThat(extracted.pages().get(1).pageNumber()).isEqualTo(2);
		assertThat(extracted.pages().get(1).text()).isBlank();
	}

	@Test
	void rejectsImageOnlyPdf() throws Exception {
		byte[] pdf = createBlankPdf();
		assertThatThrownBy(() -> service.extractFromBytes(pdf))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("extractable text");
	}

	@Test
	void roundTripsThroughPagesStore() throws Exception {
		byte[] pdf = createMultiPagePdf("One", "Two");
		ExtractedDocument extracted = service.extractFromBytes(pdf);
		ExtractedPagesStore store = new ExtractedPagesStore();
		var path = java.nio.file.Files.createTempFile("doc", ".pdf");
		try {
			java.nio.file.Files.write(path, pdf);
			var pagesPath = store.write(path, extracted);
			ExtractedDocument loaded = store.read(pagesPath);
			assertThat(loaded.pageCount()).isEqualTo(2);
			assertThat(loaded.pages().get(0).text()).contains("One");
			assertThat(loaded.pages().get(1).text()).contains("Two");
			store.deleteQuietly(pagesPath);
		}
		finally {
			java.nio.file.Files.deleteIfExists(path);
		}
	}

	private static byte[] createMultiPagePdf(String page1, String page2) throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			addTextPage(document, page1);
			addTextPage(document, page2);
			document.save(out);
			return out.toByteArray();
		}
	}

	private static byte[] createPdfWithBlankSecondPage(String page1) throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			addTextPage(document, page1);
			document.addPage(new PDPage());
			document.save(out);
			return out.toByteArray();
		}
	}

	private static byte[] createBlankPdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(out);
			return out.toByteArray();
		}
	}

	private static void addTextPage(PDDocument document, String text) throws Exception {
		PDPage page = new PDPage();
		document.addPage(page);
		try (PDPageContentStream content = new PDPageContentStream(document, page)) {
			content.beginText();
			content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
			content.newLineAtOffset(50, 700);
			content.showText(text);
			content.endText();
		}
	}
}
