// A tall plot does not stretch the bars over its whole height (OLD ChartViewport.kt:431-435, 5.20.0):
// on a pane taller than it is wide (a phone held upright, a stacked multi-chart cell) the bars take
// at most width/height of it, never less than half; the air is shared out in the margins' own
// proportion. A wide pane — every desktop chart — is not touched.

/** The smallest share of a tall pane the bars may take (OLD MIN_TALL_FILL). */
export const MIN_TALL_FILL = 0.5;

/** The price scale's top and bottom margins (shares of the height) for a pane of this size. */
export function tallMargins(top: number, bottom: number, width: number, height: number): { top: number; bottom: number } {
  const usableByMargins = 1 - top - bottom;
  const fill = width > 0 && height > width ? Math.max(width / height, MIN_TALL_FILL) : 1;
  const usable = Math.min(usableByMargins, fill);
  const extra = usableByMargins - usable;
  if (extra <= 0) return { top, bottom };
  const sum = top + bottom;
  return sum > 0
    ? { top: top + (extra * top) / sum, bottom: bottom + (extra * bottom) / sum }
    : { top: top + extra / 2, bottom: bottom + extra / 2 };
}
