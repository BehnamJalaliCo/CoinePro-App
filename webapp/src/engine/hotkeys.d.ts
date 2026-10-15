export const SHORTCUTS: { id: string; group: string; combo: string; label: string }[];
export function attachHotkeys(handlers: Record<string, (e: KeyboardEvent) => void>, opts?: { target?: EventTarget; enabled?: boolean | (() => boolean) }): () => void;
