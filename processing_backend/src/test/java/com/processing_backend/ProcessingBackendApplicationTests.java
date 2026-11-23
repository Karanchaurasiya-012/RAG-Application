package com.processing_backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context load needs live PostgreSQL/Flyway. Covered by Phase 17 live smoke instead.
 */
@SpringBootTest
@Disabled("Requires live document_db; use phase17 regression smoke for context verification")
class ProcessingBackendApplicationTests {

	@Test
	void contextLoads() {
	}
}
