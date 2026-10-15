const clone = (value) => JSON.parse(JSON.stringify(value));

/**
 * تاریخچهٔ snapshot برای ترسیم‌ها؛ مستقل از DOM و قابل‌آزمون.
 * آرایه‌ها عمداً درجا خالی می‌شوند تا aliasهای تشخیصی DrawingLayer پایدار بمانند.
 */
export class DrawingHistory {
  constructor(limit = 100) {
    if (!Number.isInteger(limit) || limit < 1) throw new RangeError('history limit must be a positive integer');
    this.limit = limit;
    this.past = [];
    this.future = [];
  }

  record(current) {
    this.past.push(clone(current));
    if (this.past.length > this.limit) this.past.splice(0, this.past.length - this.limit);
    this.future.length = 0;
  }

  undo(current) {
    if (!this.past.length) return null;
    this.future.push(clone(current));
    return clone(this.past.pop());
  }

  redo(current) {
    if (!this.future.length) return null;
    this.past.push(clone(current));
    return clone(this.future.pop());
  }

  canUndo() { return this.past.length > 0; }
  canRedo() { return this.future.length > 0; }
}
