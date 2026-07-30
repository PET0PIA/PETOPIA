import type { InputHTMLAttributes } from "react";

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`h-12 w-full rounded-button border border-line bg-card px-4 text-sm text-ink placeholder:text-muted focus:border-primary ${className}`} {...props} />;
}
