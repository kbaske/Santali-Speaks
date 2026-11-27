import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RateLimiter } from '../src/ratelimit.mjs';

test('allows up to the limit then blocks with a retry hint', () => {
  let t = 0;
  const rl = new RateLimiter({ limit: 2, windowMs: 60_000, now: () => t });
  assert.equal(rl.hit('a').allowed, true);
  assert.equal(rl.hit('a').allowed, true);
  const blocked = rl.hit('a');
  assert.equal(blocked.allowed, false);
  assert.ok(blocked.retryAfterSeconds >= 1 && blocked.retryAfterSeconds <= 60);
});

test('limits are per key', () => {
  const rl = new RateLimiter({ limit: 1, now: () => 0 });
  assert.equal(rl.hit('a').allowed, true);
  assert.equal(rl.hit('b').allowed, true);
  assert.equal(rl.hit('a').allowed, false);
});

test('window resets after it elapses and expired keys are pruned', () => {
  let t = 0;
  const rl = new RateLimiter({ limit: 1, windowMs: 1000, now: () => t });
  rl.hit('a');
  assert.equal(rl.hit('a').allowed, false);
  t = 1000;
  assert.equal(rl.hit('a').allowed, true);
  t = 5000;
  rl.hit('z');
  assert.equal(rl.windows.has('a'), false);
});

test('rejects invalid limits', () => {
  assert.throws(() => new RateLimiter({ limit: 0 }), RangeError);
  assert.throws(() => new RateLimiter({ limit: 1.5 }), RangeError);
});
