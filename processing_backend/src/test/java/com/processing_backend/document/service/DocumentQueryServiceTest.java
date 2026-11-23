package com.processing_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.processing_backend.document.entity.PiiFinding;
import com.processing_backend.pii.PiiEntityType;
import com.processing_backend.pii.PiiSummaryItem;
import org.junit.jupiter.api.Test;

class DocumentQueryServiceTest {

	@Test
	void aggregatesPiiMetadataWithoutRawValues() {
		UUID docId = UUID.randomUUID();
		PiiFinding email = new PiiFinding();
		email.setDocumentId(docId);
		email.setEntityType(PiiEntityType.EMAIL_ADDRESS);
		email.setPageNumber(1);
		email.setOccurrenceCount(2);

		PiiFinding email2 = new PiiFinding();
		email2.setDocumentId(docId);
		email2.setEntityType(PiiEntityType.EMAIL_ADDRESS);
		email2.setPageNumber(2);
		email2.setOccurrenceCount(1);

		List<PiiSummaryItem> summary = DocumentQueryService.toPiiSummary(List.of(email, email2));
		assertThat(summary).hasSize(1);
		assertThat(summary.get(0).type()).isEqualTo(PiiEntityType.EMAIL_ADDRESS);
		assertThat(summary.get(0).count()).isEqualTo(3);
		assertThat(summary.get(0).pages()).containsExactly(1, 2);
	}
}
