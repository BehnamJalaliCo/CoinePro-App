// Pure live-price interpolation step. The caller owns scheduling and persists
// `display` / `lastApplied`; keeping this pure makes idle and convergence
// behavior deterministic and independently testable.
export const LIVE_PRICE_EASING_FACTOR = 0.18;

export function advanceLivePriceFrame(state, minMove) {
  const target = Number(state?.target);
  const display = Number(state?.display);
  const lastApplied = state?.lastApplied;

  if (!Number.isFinite(target) || !Number.isFinite(display)) {
    return { value: null, shouldApply: false, settled: true, epsilon: null };
  }

  const move = Math.abs(Number(minMove));
  const numericFloor = Number.EPSILON * Math.max(1, Math.abs(target), Math.abs(display));
  // Changes below half a visible price step cannot move the rendered quote.
  const epsilon = Math.max(Number.isFinite(move) ? move * 0.5 : 0, numericFloor);
  const delta = target - display;
  const interpolated = display + delta * LIVE_PRICE_EASING_FACTOR;
  const settled = Object.is(display, target) || Math.abs(target - interpolated) <= epsilon;
  const value = settled ? target : interpolated;

  return {
    value,
    shouldApply: !Object.is(value, lastApplied),
    settled,
    epsilon,
  };
}
