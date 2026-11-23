package com.processing_backend.pii;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.PdfPageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PiiDetectionService {

	private static final Logger log = LoggerFactory.getLogger(PiiDetectionService.class);

	private final List<PiiRecognizer> recognizers;

	public PiiDetectionService(List<PiiRecognizer> recognizers) {
		this.recognizers = List.copyOf(recognizers);
	}

	public PiiDetectionResult detect(ExtractedDocument extracted) {
		List<PiiSpan> raw = new ArrayList<>();
		for (PdfPageContent page : extracted.pages()) {
			for (PiiRecognizer recognizer : recognizers) {
				raw.addAll(recognizer.detect(page));
			}
		}

		List<PiiSpan> resolved = resolveOverlaps(raw);
		List<PiiSummaryItem> summary = summarize(resolved);

		// Never log matched values — only aggregate counts by type.
		log.info(
				"PII scan complete hasPii={} spanCount={} types={}",
				!resolved.isEmpty(),
				resolved.size(),
				summary.stream().map(s -> s.type() + ":" + s.count()).toList()
		);

		return new PiiDetectionResult(!resolved.isEmpty(), List.copyOf(resolved), List.copyOf(summary));
	}

	/**
	 * Prefer higher confidence; on ties prefer longer spans. Drop overlapping losers.
	 */
	static List<PiiSpan> resolveOverlaps(List<PiiSpan> spans) {
		List<PiiSpan> ranked = spans.stream()
				.sorted(Comparator
						.comparingDouble(PiiSpan::confidence).reversed()
						.thenComparing(Comparator.comparingInt(PiiSpan::length).reversed())
						.thenComparingInt(PiiSpan::pageNumber)
						.thenComparingInt(PiiSpan::startOffset))
				.toList();

		List<PiiSpan> kept = new ArrayList<>();
		for (PiiSpan candidate : ranked) {
			boolean overlaps = kept.stream().anyMatch(candidate::overlaps);
			if (!overlaps) {
				kept.add(candidate);
			}
		}
		kept.sort(Comparator.comparingInt(PiiSpan::pageNumber).thenComparingInt(PiiSpan::startOffset));
		return kept;
	}

	private static List<PiiSummaryItem> summarize(List<PiiSpan> spans) {
		Map<PiiEntityType, List<PiiSpan>> byType = new LinkedHashMap<>();
		for (PiiSpan span : spans) {
			byType.computeIfAbsent(span.entityType(), key -> new ArrayList<>()).add(span);
		}
		List<PiiSummaryItem> summary = new ArrayList<>();
		for (Map.Entry<PiiEntityType, List<PiiSpan>> entry : byType.entrySet()) {
			TreeSet<Integer> pages = new TreeSet<>();
			for (PiiSpan span : entry.getValue()) {
				pages.add(span.pageNumber());
			}
			summary.add(new PiiSummaryItem(entry.getKey(), entry.getValue().size(), List.copyOf(pages)));
		}
		return summary;
	}
}
