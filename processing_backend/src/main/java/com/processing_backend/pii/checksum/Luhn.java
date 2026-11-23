package com.processing_backend.pii.checksum;

public final class Luhn {

	private Luhn() {
	}

	public static boolean isValid(String digits) {
		int sum = 0;
		boolean alternate = false;
		for (int i = digits.length() - 1; i >= 0; i--) {
			int n = digits.charAt(i) - '0';
			if (n < 0 || n > 9) {
				return false;
			}
			if (alternate) {
				n *= 2;
				if (n > 9) {
					n -= 9;
				}
			}
			sum += n;
			alternate = !alternate;
		}
		return sum % 10 == 0;
	}
}
