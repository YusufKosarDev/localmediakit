/**
 * The dashboard's HTTP layer.
 *
 * Every handler used to repeat the same four lines: build the auth header,
 * check res.ok, try to read {error} off the body, fall back to an
 * "(HTTP nnn)" string. That pattern is here once now, so a panel reads as the
 * flow it implements rather than as fetch bookkeeping.
 */

import { DEFAULT_LOCALE, type Locale } from "@/app/_i18n";
import { translateError } from "@/app/_i18n/errors";

export const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export function authHeaders(): HeadersInit {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  return { "Content-Type": "application/json", Authorization: `Bearer ${token ?? ""}` };
}

/** Turkish number formatting, used wherever a count is rendered. */
export const nf = (n: number) => n.toLocaleString("tr-TR");

/**
 * The backend's error shape is {status, error}. A body that is missing or not
 * JSON falls back to the status code, which is what the previous inline
 * handlers did.
 */
/**
 * The API answers with a machine `code` and a human `error`. A code the client
 * knows is translated into the reader's language; anything else falls back to
 * the API's own message, then to the caller's label plus the status.
 */
export async function errorMessage(
  res: Response,
  fallback: string,
  locale: Locale = DEFAULT_LOCALE
): Promise<string> {
  const body = await res.json().catch(() => null);
  const translated = translateError(body?.code, body?.error, locale);
  return translated ?? `${fallback} (HTTP ${res.status})`;
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

async function request(method: Method, path: string, body?: unknown): Promise<Response> {
  return fetch(`${BACKEND}${path}`, {
    method,
    headers: authHeaders(),
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

/**
 * A read that either yields data or yields nothing. Loaders in the old code
 * uniformly ignored failed reads (`if (res.ok) setX(...)`), and that is
 * preserved: callers decide whether silence deserves a message.
 */
export async function get<T>(path: string): Promise<T | null> {
  const res = await request("GET", path);
  return res.ok ? ((await res.json()) as T) : null;
}

export type Result<T> = { ok: true; data: T } | { ok: false; message: string };

/**
 * A write. `expect` pins the success status the old code checked for —
 * several endpoints answer 201 or 204 and were tested for exactly that, so
 * treating any 2xx as success would have loosened those checks.
 */
async function write<T>(
  method: Method,
  path: string,
  body: unknown,
  fallback: string,
  expect?: number
): Promise<Result<T>> {
  const res = await request(method, path, body);
  const succeeded = expect === undefined ? res.ok : res.status === expect;
  if (!succeeded) {
    return { ok: false, message: await errorMessage(res, fallback) };
  }
  // 204 carries no body; callers that need nothing back ask for Result<void>.
  const data = res.status === 204 ? undefined : await res.json().catch(() => undefined);
  return { ok: true, data: data as T };
}

export function post<T = void>(path: string, body?: unknown, fallback = "Request failed", expect?: number) {
  return write<T>("POST", path, body, fallback, expect);
}

export function put<T = void>(path: string, body?: unknown, fallback = "Request failed", expect?: number) {
  return write<T>("PUT", path, body, fallback, expect);
}

export function del<T = void>(path: string, fallback = "Request failed", expect = 204) {
  return write<T>("DELETE", path, undefined, fallback, expect);
}
