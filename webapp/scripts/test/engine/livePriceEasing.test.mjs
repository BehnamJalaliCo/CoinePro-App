import assert from 'node:assert/strict';
import test from 'node:test';

import { advanceLivePriceFrame, LIVE_PRICE_EASING_FACTOR } from '../../../src/engine/livePriceEasing.js';

test('settled live price stays idle without another apply', () => {
  const state = { target: 1.23456, display: 1.23456, lastApplied: 1.23456 };
  const step = advanceLivePriceFrame(state, 0.00001);

  assert.equal(step.value, state.target);
  assert.equal(step.settled, true);
  assert.equal(step.shouldApply, false);
});

test('live price converges monotonically and snaps to the real target in bounded frames', () => {
  const target = 1.25;
  const minMove = 0.00001;
  const state = { target, display: 1.2, lastApplied: 1.2 };
  let frames = 0;
  let previous = state.display;

  while (frames < 100) {
    const step = advanceLivePriceFrame(state, minMove);
    frames += 1;
    assert.ok(step.value >= previous && step.value <= target, `frame ${frames} must not overshoot`);
    if (step.shouldApply) state.lastApplied = step.value;
    state.display = step.value;
    previous = step.value;
    if (step.settled) break;
  }

  assert.equal(LIVE_PRICE_EASING_FACTOR, 0.18);
  assert.ok(frames < 50, `expected bounded convergence, got ${frames} frames`);
  assert.equal(state.display, target);
  assert.equal(state.lastApplied, target);

  const idle = advanceLivePriceFrame(state, minMove);
  assert.equal(idle.settled, true);
  assert.equal(idle.shouldApply, false);
});
