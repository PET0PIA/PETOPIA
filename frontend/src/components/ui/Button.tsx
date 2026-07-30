import type { ButtonHTMLAttributes, ReactNode } from "react";

type ButtonVariant = "primary" | "secondary" | "outline" | "ghost";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  children: ReactNode;
}

const variants: Record<ButtonVariant, string> = {
  primary: "bg-primary text-white hover:opacity-90",
  secondary: "bg-sun text-ink hover:opacity-90",
  outline: "border border-line bg-card text-ink hover:bg-page",
  ghost: "text-muted hover:bg-page hover:text-ink",
};

export function Button({ className = "", variant = "primary", type = "button", children, ...props }: ButtonProps) {
  return <button type={type} className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-button px-4 text-sm font-bold transition disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`} {...props}>{children}</button>;
}
