package com.processing_backend.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfValidationService {

	private static final Logger log = LoggerFactory.getLogger(PdfValidationService.class);
	private static final byte[] PDF_MAGIC = new byte[] {'%', 'P', 'D', 'F', '-'};
	private static final long MAX_EXTRACTED_TEXT_BYTES = 10L * 1024L * 1024L;

	private final AppProperties appProperties;

	public PdfValidationService(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	public ValidatedPdf validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException("PDF file is required and must not be empty.", HttpStatus.BAD_REQUEST);
		}

		long maxBytes = appProperties.getPdf().getMaxSizeMb() * 1024L * 1024L;
		if (file.getSize() <= 0) {
			throw new BusinessException("PDF file is empty.", HttpStatus.BAD_REQUEST);
		}
		if (file.getSize() > maxBytes) {
			throw new BusinessException(
					"PDF exceeds maximum size of " + appProperties.getPdf().getMaxSizeMb() + " MB.",
					HttpStatus.BAD_REQUEST
			);
		}

		String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
		if (!sanitizedFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			throw new BusinessException("Only .pdf files are accepted.", HttpStatus.BAD_REQUEST);
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException ex) {
			throw new BusinessException("Unable to read uploaded file.", HttpStatus.BAD_REQUEST);
		}

		if (bytes.length == 0) {
			throw new BusinessException("PDF file is empty.", HttpStatus.BAD_REQUEST);
		}
		if (!hasPdfSignature(bytes)) {
			throw new BusinessException("File is not a valid PDF (signature check failed).", HttpStatus.BAD_REQUEST);
		}

		String checksum = sha256Hex(bytes);
		int pageCount = inspectWithPdfBox(bytes);

		return new ValidatedPdf(
				bytes,
				sanitizedFilename,
				"application/pdf",
				checksum,
				pageCount
		);
	}

	private int inspectWithPdfBox(byte[] bytes) {
		try (PDDocument document = Loader.loadPDF(bytes)) {
			if (document.isEncrypted()) {
				throw new BusinessException("Encrypted PDFs are not supported.", HttpStatus.BAD_REQUEST);
			}

			int pages = document.getNumberOfPages();
			if (pages <= 0) {
				throw new BusinessException("PDF has no pages.", HttpStatus.BAD_REQUEST);
			}
			if (pages > appProperties.getPdf().getMaxPages()) {
				throw new BusinessException(
						"PDF exceeds maximum page count of " + appProperties.getPdf().getMaxPages() + ".",
						HttpStatus.BAD_REQUEST
				);
			}

			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(pages);
			String text = stripper.getText(document);
			if (text == null || text.isBlank()) {
				throw new BusinessException(
						"PDF has no extractable text. Scanned/image-only PDFs are not supported yet.",
						HttpStatus.BAD_REQUEST
				);
			}
			if (text.getBytes().length > MAX_EXTRACTED_TEXT_BYTES) {
				throw new BusinessException(
						"Extracted text exceeds the maximum allowed size of 10 MB.",
						HttpStatus.BAD_REQUEST
				);
			}

			return pages;
		}
		catch (InvalidPasswordException ex) {
			throw new BusinessException("Encrypted PDFs are not supported.", HttpStatus.BAD_REQUEST);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (IOException ex) {
			log.warn("PDF parse failed: {}", ex.getClass().getSimpleName());
			throw new BusinessException("PDF is corrupted or unreadable.", HttpStatus.BAD_REQUEST);
		}
	}

	private static boolean hasPdfSignature(byte[] bytes) {
		if (bytes.length < PDF_MAGIC.length) {
			return false;
		}
		for (int i = 0; i < PDF_MAGIC.length; i++) {
			if (bytes[i] != PDF_MAGIC[i]) {
				return false;
			}
		}
		return true;
	}

	private static String sanitizeFilename(String original) {
		if (original == null || original.isBlank()) {
			return "document.pdf";
		}
		String name = original.replace('\\', '/');
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		name = name.replaceAll("[\\r\\n\\t\\x00]", "").trim();
		if (name.isBlank() || name.equals(".") || name.equals("..")) {
			return "document.pdf";
		}
		if (name.length() > 200) {
			name = name.substring(name.length() - 200);
		}
		return name;
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	/** Exposed for tests / future streaming paths. */
	public static boolean looksLikePdf(InputStream in) throws IOException {
		byte[] header = in.readNBytes(PDF_MAGIC.length);
		return hasPdfSignature(header);
	}
}
