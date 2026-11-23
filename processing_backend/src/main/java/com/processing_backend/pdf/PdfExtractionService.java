package com.processing_backend.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.processing_backend.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PdfExtractionService {

	private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

	public ExtractedDocument extractFromBytes(byte[] pdfBytes) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			throw new BusinessException("PDF bytes are required for extraction.", HttpStatus.BAD_REQUEST);
		}
		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			return extract(document);
		}
		catch (InvalidPasswordException ex) {
			throw new BusinessException("Encrypted PDFs are not supported.", HttpStatus.BAD_REQUEST);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (IOException ex) {
			log.warn("PDF extraction failed: {}", ex.getClass().getSimpleName());
			throw new BusinessException("PDF text extraction failed.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	public ExtractedDocument extractFromPath(Path pdfPath) {
		if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
			throw new BusinessException("PDF temp file is missing for extraction.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		try (RandomAccessReadBufferedFile rar = new RandomAccessReadBufferedFile(pdfPath.toFile());
				PDDocument document = Loader.loadPDF(rar)) {
			return extract(document);
		}
		catch (InvalidPasswordException ex) {
			throw new BusinessException("Encrypted PDFs are not supported.", HttpStatus.BAD_REQUEST);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (IOException ex) {
			log.warn("PDF extraction failed for path: {}", ex.getClass().getSimpleName());
			throw new BusinessException("PDF text extraction failed.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	private ExtractedDocument extract(PDDocument document) throws IOException {
		if (document.isEncrypted()) {
			throw new BusinessException("Encrypted PDFs are not supported.", HttpStatus.BAD_REQUEST);
		}

		int pageCount = document.getNumberOfPages();
		if (pageCount <= 0) {
			throw new BusinessException("PDF has no pages to extract.", HttpStatus.BAD_REQUEST);
		}

		PDFTextStripper stripper = new PDFTextStripper();
		List<PdfPageContent> pages = new ArrayList<>(pageCount);
		int nonEmpty = 0;
		int totalChars = 0;

		for (int page = 1; page <= pageCount; page++) {
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			String text = stripper.getText(document);
			if (text == null) {
				text = "";
			}
			// Preserve empty pages for citation alignment; trim only trailing page-break noise.
			String normalized = text.replace("\u0000", "");
			pages.add(new PdfPageContent(page, normalized));
			if (!normalized.isBlank()) {
				nonEmpty++;
			}
			totalChars += normalized.length();
		}

		if (nonEmpty == 0) {
			throw new BusinessException(
					"PDF has no extractable text. Scanned/image-only PDFs are not supported yet.",
					HttpStatus.BAD_REQUEST
			);
		}

		// Never log page text — only aggregate metrics.
		log.info("Extracted PDF pages pageCount={} nonEmptyPages={} totalChars={}", pageCount, nonEmpty, totalChars);
		return new ExtractedDocument(List.copyOf(pages), pageCount, nonEmpty, totalChars);
	}
}
