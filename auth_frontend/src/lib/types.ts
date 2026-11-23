export type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T | null;
};

export type User = {
  id: string;
  fullName: string;
  email: string;
  emailVerified: boolean;
  roles: string[];
};

export type AuthPayload = {
  user: User;
  accessToken?: string;
};

export type AccessTokenPayload = {
  accessToken: string;
};

export type CsrfPayload = {
  headerName: string;
  parameterName: string;
  token: string;
};
