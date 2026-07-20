import * as React from "react";
import { cn } from "./cn";

/* Hand-built, token-driven primitives (no runtime UI dependency). No hooks, so
   they render in both server and client components. */

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md";
};

export function Button({
  className,
  variant = "primary",
  size = "md",
  ...props
}: ButtonProps) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-xl font-medium transition-colors " +
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/50 " +
    "disabled:opacity-50 disabled:pointer-events-none";
  const sizes = { sm: "h-8 px-3 text-sm", md: "h-10 px-4 text-sm" };
  const variants = {
    primary: "bg-brand-strong text-white hover:opacity-90 shadow-sm",
    secondary: "bg-surface text-fg border border-line hover:bg-page",
    ghost: "text-muted hover:text-fg hover:bg-page",
    danger: "bg-surface text-danger border border-line hover:bg-danger/10",
  };
  return (
    <button className={cn(base, sizes[size], variants[variant], className)} {...props} />
  );
}

export const Input = React.forwardRef<
  HTMLInputElement,
  React.InputHTMLAttributes<HTMLInputElement>
>(function Input({ className, ...props }, ref) {
  return (
    <input
      ref={ref}
      className={cn(
        "h-10 w-full rounded-xl border border-line bg-surface px-3 text-sm text-fg",
        "placeholder:text-faint transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40 focus-visible:border-brand/50",
        className
      )}
      {...props}
    />
  );
});

export function Card({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "rounded-2xl border border-line bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.04)]",
        className
      )}
      {...props}
    />
  );
}

export function Badge({
  className,
  tone = "neutral",
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & {
  tone?: "neutral" | "brand" | "success" | "danger" | "warning";
}) {
  const tones = {
    neutral: "bg-page text-muted border-line",
    brand: "bg-brand-weak text-brand border-transparent",
    success: "bg-success/10 text-success border-transparent",
    danger: "bg-danger/10 text-danger border-transparent",
    warning: "bg-amber-100 text-amber-700 border-transparent",
  };
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium",
        tones[tone],
        className
      )}
      {...props}
    />
  );
}

export function Label({
  className,
  ...props
}: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return (
    <label className={cn("text-sm font-medium text-fg", className)} {...props} />
  );
}
