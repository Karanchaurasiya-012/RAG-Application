type SubmitButtonProps = {
  loading?: boolean;
  children: string;
};

export function SubmitButton({ loading, children }: SubmitButtonProps) {
  return (
    <button type="submit" className="btn btn-primary" disabled={loading}>
      {loading ? "Please wait…" : children}
    </button>
  );
}
