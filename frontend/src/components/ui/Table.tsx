import type { TableHTMLAttributes } from "react";

export function Table({ className = "", ...props }: TableHTMLAttributes<HTMLTableElement>) {
  return <div className="overflow-x-auto rounded-card border border-line"><table className={`w-full border-collapse text-left text-sm ${className}`} {...props} /></div>;
}
