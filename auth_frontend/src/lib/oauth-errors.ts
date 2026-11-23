const MESSAGES: Record<string, string> = {
  account_exists_use_password_or_link:
    "An account with this email already exists. Sign in with your password.",
  google_profile_invalid: "Google did not return a usable profile. Try again.",
  oauth_failed: "Google sign-in failed. Please try again.",
};

export function oauthErrorMessage(code: string | null | undefined): string | null {
  if (!code) {
    return null;
  }
  return MESSAGES[code] || "Google sign-in failed. Please try again.";
}
