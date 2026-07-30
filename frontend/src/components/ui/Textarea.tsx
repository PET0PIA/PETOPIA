import type { TextareaHTMLAttributes } from "react";

export function Textarea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={`min-h-28 w-full rounded-button border border-line bg-card px-4 py-3 text-sm text-ink placeholder:text-muted focus:border-primary ${className}`} {...props} />;
}
