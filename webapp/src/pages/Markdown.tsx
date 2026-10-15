import { Fragment, type ReactNode } from 'react';

// A small Markdown reader for the legal texts and the help library: headings, paragraphs, lists,
// rules, bold, italics, inline code and links. Nothing is injected as HTML.
function inline(text: string, key = 0): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /(\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\))/g;
  let last = 0; let m: RegExpExecArray | null; let i = 0;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index));
    const k = `${key}-${i++}`;
    if (m[2]) out.push(<strong key={k}>{inline(m[2], i)}</strong>);
    else if (m[3]) out.push(<em key={k}>{m[3]}</em>);
    else if (m[4]) out.push(<code key={k}>{m[4]}</code>);
    else if (m[5] && m[6]) {
      const safe = /^(https?:|mailto:|#|\/)/.test(m[6]) ? m[6] : '#';
      out.push(<a key={k} href={safe} target={safe.startsWith('http') ? '_blank' : undefined} rel="noreferrer">{m[5]}</a>);
    }
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

export function Markdown({ source }: { source: string }) {
  const blocks: ReactNode[] = [];
  const lines = source.replace(/\r/g, '').split('\n');
  let para: string[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;
  const flush = () => {
    if (para.length) { blocks.push(<p key={blocks.length}>{inline(para.join(' '))}</p>); para = []; }
    if (list) {
      const items = list.items.map((x, i) => <li key={i}>{inline(x)}</li>);
      blocks.push(list.ordered ? <ol key={blocks.length}>{items}</ol> : <ul key={blocks.length}>{items}</ul>);
      list = null;
    }
  };
  for (const raw of lines) {
    const line = raw.trimEnd();
    const h = /^(#{1,4})\s+(.*)$/.exec(line);
    const li = /^\s*([*-]|\d+[.)]|[۰-۹]+[.)])\s+(.*)$/.exec(line);
    if (!line.trim()) { flush(); continue; }
    if (/^-{3,}$|^\*{3,}$/.test(line.trim())) { flush(); blocks.push(<hr key={blocks.length} />); continue; }
    if (h) { flush(); const Tag = (`h${Math.min(4, h[1]!.length + 1)}`) as 'h2'; blocks.push(<Tag key={blocks.length}>{inline(h[2]!)}</Tag>); continue; }
    if (li) {
      if (para.length) flush();
      const ordered = /\d|[۰-۹]/.test(li[1]!);
      if (!list || list.ordered !== ordered) { flush(); list = { ordered, items: [] }; }
      list.items.push(li[2]!);
      continue;
    }
    if (list && /^\s{2,}/.test(raw)) { list.items[list.items.length - 1] += ` ${line.trim()}`; continue; }
    if (list) flush();
    para.push(line.trim());
  }
  flush();
  return <div className="md" dir="auto">{blocks.map((b, i) => <Fragment key={i}>{b}</Fragment>)}</div>;
}
