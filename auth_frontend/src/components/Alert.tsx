type AlertProps = {
  tone?: "error" | "success" | "info";
  children: string;
};

export function Alert({ tone = "info", children }: AlertProps) {
  return <div className={`alert alert-${tone}`} role="status">{children}</div>;
}
