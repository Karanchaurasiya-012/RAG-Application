package com.processing_backend.pii;

import java.util.List;

import com.processing_backend.pdf.PdfPageContent;

public interface PiiRecognizer {

	PiiEntityType entityType();

	List<PiiSpan> detect(PdfPageContent page);
}
