// Fixed-window rate limiter keyed by client address. Protects the BHASHINI quota
// and the server from abuse. Memory is bounded: expired windows are pruned periodically.

export class RateLimiter {
  /**
   * @param {{limit: number, windowMs?: number, now?: () => number}} options
   */
  constructor({ limit, windowMs = 60_000, now = () => Date.now() }) {
    if (!Number.isInteger(limit) || limit < 1) {
      throw new RangeError('limit must be a positive integer');
    }
    this.limit = limit;
    this.windowMs = windowMs;
    this.now = now;
    /** @type {Map<string, {start: number, count: number}>} */
    this.windows = new Map();
    this.lastPrune = this.now();
  }

  /**
   * Record a request for `key`.
   * @returns {{allowed: boolean, retryAfterSeconds: number}}
   */
  hit(key) {
    const t = this.now();
    this.#pruneIfDue(t);

    let window = this.windows.get(key);
    if (!window || t - window.start >= this.windowMs) {
      window = { start: t, count: 0 };
      this.windows.set(key, window);
    }
    window.count += 1;
    if (window.count > this.limit) {
      const retryAfterMs = this.windowMs - (t - window.start);
      return { allowed: false, retryAfterSeconds: Math.max(1, Math.ceil(retryAfterMs / 1000)) };
    }
    return { allowed: true, retryAfterSeconds: 0 };
  }

  #pruneIfDue(t) {
    if (t - this.lastPrune < this.windowMs) {
      return;
    }
    for (const [key, window] of this.windows) {
      if (t - window.start >= this.windowMs) {
        this.windows.delete(key);
      }
    }
    this.lastPrune = t;
  }
}
