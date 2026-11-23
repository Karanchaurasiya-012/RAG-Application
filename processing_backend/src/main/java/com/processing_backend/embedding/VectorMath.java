package com.processing_backend.embedding;

import com.processing_backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

final class VectorMath {

	private VectorMath() {
	}

	/**
	 * L2-normalize in place copy. Required for gemini-embedding-001 when output
	 * dimensionality is not the native 3072.
	 */
	static float[] l2Normalize(float[] values) {
		double sumSquares = 0.0;
		for (float v : values) {
			sumSquares += (double) v * (double) v;
		}
		double norm = Math.sqrt(sumSquares);
		if (!(norm > 0.0) || !Double.isFinite(norm)) {
			throw new BusinessException("Cannot L2-normalize a zero or non-finite embedding.", HttpStatus.BAD_GATEWAY);
		}
		float[] out = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = (float) (values[i] / norm);
		}
		return out;
	}
}
