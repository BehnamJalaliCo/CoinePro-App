// What a failed sign-in, sign-up or reset says (OLD core/auth AuthFailureMapping.kt,
// feature/auth EmailAuthScreen.kt). The line that matters is between a refusal and a non-answer:
// a server that said no has spent an attempt and its words explain why; a request that never
// arrived has no words at all; a 5xx *is* an answer — the fault is on the other side; and an answer
// that came but could not be read is a fault at our end. Only a real verdict carries the server's
// own words. A «too many» carries the wait the server asked for, which the button counts down.
import { HttpError } from './http';

/** An answer arrived and this side could not read it (no token in a 200, a body that is not JSON). */
export class UnreadableAnswer extends Error {
  constructor() { super('unreadable'); this.name = 'UnreadableAnswer'; }
}

export type AuthFailureReason = 'rejected' | 'invalid' | 'rateLimited' | 'unreachable' | 'serverFault' | 'unreadable';
export interface AuthFailure { reason: AuthFailureReason; message: string | null; retryAfterSeconds: number | null }

export function authFailure(e: unknown): AuthFailure {
  if (e instanceof UnreadableAnswer || e instanceof SyntaxError || e instanceof TypeError) return { reason: 'unreadable', message: null, retryAfterSeconds: null };
  if (!(e instanceof HttpError)) return { reason: 'unreadable', message: null, retryAfterSeconds: null };
  const words = e.readerMessage;
  if (e.status === -1 || e.status === 0) return { reason: 'unreachable', message: null, retryAfterSeconds: null };
  if (e.status === 429) return { reason: 'rateLimited', message: words, retryAfterSeconds: e.retryAfterMs ? Math.ceil(e.retryAfterMs / 1000) : null };
  if (e.status >= 500) return { reason: 'serverFault', message: words, retryAfterSeconds: null };
  if (e.status === 401 || e.status === 403) return { reason: 'rejected', message: words, retryAfterSeconds: null };
  if (e.status === 400 || e.status === 409 || e.status === 422) return { reason: 'invalid', message: words, retryAfterSeconds: null };
  // A 2xx-shaped body could not be read, or a status this build does not know.
  return { reason: e.status >= 200 && e.status < 300 ? 'unreadable' : 'rejected', message: words, retryAfterSeconds: null };
}

export const AUTH_FAILURE_TEXT: Record<AuthFailureReason, [string, string]> = {
  rejected: ['درخواست پذیرفته نشد.', 'The request was not accepted.'],
  invalid: ['اطلاعات واردشده پذیرفته نشد.', 'Those details were not accepted.'],
  rateLimited: ['تلاش‌های زیادی انجام شد.', 'Too many attempts.'],
  unreachable: ['پاسخی نرسید. این یعنی درخواست بررسی نشد، نه اینکه رد شد.', 'No answer came back. That means the request was not judged, not that it was refused.'],
  serverFault: ['سرور پاسخ داد ولی خطای خودش را برگرداند. از سمت شما چیزی اشتباه نیست؛ کمی بعد دوباره تلاش کنید.', 'The server answered with a fault of its own. Nothing is wrong at your end; try again shortly.'],
  unreadable: ['سرور پاسخ داد ولی برنامه نتوانست پاسخ را بخواند. ایراد از سمت ماست، نه از اینترنت شما، و تلاش دوباره فایده‌ای ندارد — این نسخه باید به‌روز شود.', 'The server answered and this app could not read the answer. That is a fault at our end, not with your connection, and trying again will not help — this needs an app update.'],
};

/** «تلاش دوباره تا ۴۲ ثانیه‌ی دیگر» — the button's label while the server's wait runs. */
export const retryIn = (seconds: number, fa: boolean, digits: (n: number) => string) => (fa ? `تلاش دوباره تا ${digits(seconds)} ثانیه‌ی دیگر` : `You can try again in ${seconds} seconds`);
