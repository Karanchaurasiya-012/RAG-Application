package com.processing_backend.document.dto;

import com.processing_backend.document.entity.PiiDecision;
import jakarta.validation.constraints.NotNull;

public class PiiDecisionRequest {

	@NotNull(message = "decision is required.")
	private PiiDecision decision;

	public PiiDecision getDecision() {
		return decision;
	}

	public void setDecision(PiiDecision decision) {
		this.decision = decision;
	}
}
