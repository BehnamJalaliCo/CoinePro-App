// One sprite for every interface glyph: the TradingView set the phone also ships, plus the Phosphor
// icons listed in scripts/phosphor-icons.txt (prefixed `ph-`). The sprite is fetched once and
// inlined into the document so `<use href="#…">` works without a network request per icon.
let loaded: Promise<void> | null = null;

export function loadIconSprite(base: string): Promise<void> {
  if (!loaded) {
    loaded = fetch(`${base}generated/icons.svg`)
      .then((r) => r.text())
      .then((svg) => {
        const host = document.createElement('div');
        host.setAttribute('aria-hidden', 'true');
        host.style.display = 'none';
        host.innerHTML = svg;
        document.body.prepend(host);
      })
      .catch(() => undefined);
  }
  return loaded;
}

export type IconSize = 'sm' | 'md' | 'lg';

export function Icon({ name, size = 'lg', className, title }: { name: string; size?: IconSize; className?: string; title?: string }) {
  const cls = ['icon', size === 'lg' ? '' : size, className].filter(Boolean).join(' ');
  return (
    <svg className={cls} aria-hidden={title ? undefined : true} role={title ? 'img' : undefined}>
      {title ? <title>{title}</title> : null}
      <use href={`#${name}`} />
    </svg>
  );
}
