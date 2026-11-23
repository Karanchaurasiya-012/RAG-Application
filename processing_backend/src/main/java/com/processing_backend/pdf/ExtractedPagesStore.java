package com.processing_backend.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.processing_backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Temporary on-disk store for extracted page text (pre-PII / pre-chunk).
 * Uses a simple line format to avoid pulling a JSON library API surface.
 * Format: each page is {@code ===PAGE N===} followed by page text.
 */
@Service
public class ExtractedPagesStore {

	public Path pathForPdf(Path pdfTempPath) {
		return sibling(pdfTempPath, ".pages.txt");
	}

	public Path finalPathForPdf(Path pdfTempPath) {
		return sibling(pdfTempPath, ".final.pages.txt");
	}

	private Path sibling(Path pdfTempPath, String suffix) {
		String name = pdfTempPath.getFileName().toString();
		String base = name.endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
		return pdfTempPath.resolveSibling(base + suffix);
	}

	public Path write(Path pdfTempPath, ExtractedDocument extracted) {
		return writeTo(pathForPdf(pdfTempPath), extracted);
	}

	public Path writeFinal(Path pdfTempPath, ExtractedDocument extracted) {
		return writeTo(finalPathForPdf(pdfTempPath), extracted);
	}

	private Path writeTo(Path target, ExtractedDocument extracted) {
		StringBuilder sb = new StringBuilder();
		for (PdfPageContent page : extracted.pages()) {
			sb.append("===PAGE ").append(page.pageNumber()).append("===\n");
			sb.append(page.text());
			if (!page.text().endsWith("\n")) {
				sb.append('\n');
			}
		}
		try {
			Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
			return target;
		}
		catch (IOException ex) {
			throw new BusinessException("Unable to store extracted page text.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public ExtractedDocument read(Path pagesPath) {
		try {
			String raw = Files.readString(pagesPath, StandardCharsets.UTF_8);
			List<PdfPageContent> pages = new ArrayList<>();
			String[] parts = raw.split("(?m)^===PAGE ");
			int nonEmpty = 0;
			int totalChars = 0;
			for (String part : parts) {
				if (part.isBlank()) {
					continue;
				}
				int marker = part.indexOf("===\n");
				if (marker < 0) {
					continue;
				}
				int pageNumber = Integer.parseInt(part.substring(0, marker).trim());
				String text = part.substring(marker + 4);
				pages.add(new PdfPageContent(pageNumber, text));
				if (!text.isBlank()) {
					nonEmpty++;
				}
				totalChars += text.length();
			}
			return new ExtractedDocument(List.copyOf(pages), pages.size(), nonEmpty, totalChars);
		}
		catch (IOException | NumberFormatException ex) {
			throw new BusinessException("Unable to read extracted page text.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public void deleteQuietly(Path pagesPath) {
		if (pagesPath == null) {
			return;
		}
		try {
			Files.deleteIfExists(pagesPath);
		}
		catch (IOException ignored) {
			// best-effort
		}
	}
}
