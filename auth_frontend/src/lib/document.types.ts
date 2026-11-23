export type DocumentStatus =
  | "UPLOADED"
  | "VALIDATING"
  | "EXTRACTING"
  | "PII_SCANNING"
  | "PII_DETECTED"
  | "NO_PII_DETECTED"
  | "AWAITING_PII_DECISION"
  | "PREPARING_TEXT"
  | "UPLOADING_S3"
  | "CHUNKING"
  | "EMBEDDING"
  | "STORING_VECTORS"
  | "COMPLETED"
  | "VALIDATION_FAILED"
  | "EXTRACTION_FAILED"
  | "PII_SCAN_FAILED"
  | "MASKING_FAILED"
  | "UPLOAD_FAILED"
  | "CHUNKING_FAILED"
  | "EMBEDDING_FAILED"
  | "VECTOR_STORAGE_FAILED"
  | "CANCELLED"
  | "EXPIRED";

export type PiiDecision = "MASK" | "KEEP_ORIGINAL";

export type PiiSummaryItem = {
  type: string;
  count: number;
  pages: number[];
};

export type DocumentUploadResult = {
  documentId: string;
  status: DocumentStatus;
  originalFilename: string;
  fileSize: number;
  pageCount: number;
  pagesExtracted: number;
  nonEmptyPages: number;
  checksumSha256: string;
  duplicate: boolean;
  piiDetected: boolean;
  piiSummary: PiiSummaryItem[];
  availableActions: string[];
  originalPdfWillRemainUnmodified: boolean | null;
  processingStarted: boolean | null;
};

export type DocumentListItem = {
  documentId: string;
  status: DocumentStatus;
  originalFilename: string;
  fileSize: number;
  pageCount: number | null;
  piiDetected: boolean;
  piiDecision: string | null;
  maskingApplied: boolean;
  chunkCount: number | null;
  embeddingProvider: string | null;
  createdAt: string;
  updatedAt: string;
};

export type DocumentListResult = {
  items: DocumentListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DocumentStatusResult = {
  documentId: string;
  status: DocumentStatus;
  piiDetected: boolean;
  piiDecision: string | null;
  maskingApplied: boolean;
  chunkCount: number | null;
  embeddingProvider: string | null;
  downloadAvailable: boolean;
  jobStatus: string | null;
  jobType: string | null;
  jobAttempts: number | null;
  lastErrorCode: string | null;
};

export type DocumentDetails = {
  documentId: string;
  status: DocumentStatus;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  checksumSha256: string;
  pageCount: number | null;
  chunkCount: number | null;
  piiDetected: boolean;
  piiDecision: string | null;
  maskingApplied: boolean;
  piiSummary: PiiSummaryItem[];
  availableActions: string[];
  originalPdfWillRemainUnmodified: boolean;
  downloadAvailable: boolean;
  embeddingProvider: string | null;
  embeddingModel: string | null;
  embeddingDimensions: number | null;
  createdAt: string;
  updatedAt: string;
};

export type DocumentDownloadResult = {
  documentId: string;
  originalFilename: string;
  downloadUrl: string;
  expiresInSeconds: number;
};

export type PiiDecisionResult = {
  documentId: string;
  status: DocumentStatus;
  decision: PiiDecision;
  originalPdfWillRemainUnmodified: boolean;
  nextStep: string;
};
