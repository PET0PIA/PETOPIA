import type { SelectHTMLAttributes } from "react";

export function Select({ className = "", children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={`h-12 w-full rounded-button border border-line bg-card px-4 text-sm text-ink focus:border-primary ${className}`} {...props}>{children}</select>;
}
