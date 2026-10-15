// The CoinePro-FX academy (core/academy AcademyGateway): every academy/* call carries the academy
// token, minted from the forex session (user/academy-token). TradeYar has no academy.
import { request, HttpError, type RequestOptions } from './http';
import { accessFor } from './auth';
import { academyToken, dropAcademyToken } from './forexAccess';

export async function academy<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const token = await academyToken(await accessFor('coineprofx'));
  if (!token) throw new HttpError(401, null, null);
  try {
    return await request<T>(`/up/coineprofx/api/academy/${path}`, { ...opts, token });
  } catch (e) {
    if (e instanceof HttpError && e.status === 401) dropAcademyToken();
    throw e;
  }
}

/** A lesson's picture or video through the relay: the platform's own host by its passthrough, any
 *  other picture through the image proxy, so it loads where the platform's host is blocked. */
export function viaRelay(url: string, image = true): string {
  try {
    const u = new URL(url, window.location.origin);
    if (u.origin === window.location.origin) return u.pathname + u.search;
    if (/(^|\.)coineprofx\.com$/.test(u.hostname)) return `/up/coineprofx${u.pathname}${u.search}`;
    if (/(^|\.)trade-future\.ir$/.test(u.hostname)) return `/up/tradeyar${u.pathname}${u.search}`;
    return image ? `/api/img?url=${encodeURIComponent(u.href)}` : u.href;
  } catch { return url; }
}
