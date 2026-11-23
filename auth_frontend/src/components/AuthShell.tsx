import Link from "next/link";
import type { ReactNode } from "react";

type AuthShellProps = {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
};

export function AuthShell({ title, subtitle, children, footer }: AuthShellProps) {
  return (
    <div className="auth-page">
      <div className="auth-glow" aria-hidden />
      <div className="auth-card">
        <div className="auth-brand">
          <Link href="/" className="brand-mark">
            Auth<span>Backend</span>
          </Link>
        </div>
        <h1>{title}</h1>
        {subtitle ? <p className="auth-subtitle">{subtitle}</p> : null}
        <div className="auth-body">{children}</div>
        {footer ? <div className="auth-footer">{footer}</div> : null}
      </div>
    </div>
  );
}
