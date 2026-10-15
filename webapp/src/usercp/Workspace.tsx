import { useMemo, useState } from 'react';
import { HttpError } from '../data/http';
import {
  KINDS, blobBytes, collect, exportWorkspace, hashBlob, importWorkspace, panel, plan, readMemo, restoreVersion, savePrefs, syncWorkspace, writeMemo,
  type Kind, type SyncResult, type WorkspacePart,
} from '../data/panel';
import { downloadText, pickText, useL, useLoad } from '../pages/kit';
import { usePanel } from './PanelPage';
import { Card, Note, Pill, Section, Switch, bytes, stamp, useAgo, useCount, type Tone } from './ui';
import { kindLabel } from './labels';
import { Library } from './Library';

interface Version { version: number; size: number; saved_at: number; device: string | null; restored_from: number | null }

export default function Workspace() {
  const L = useL();
  const ago = useAgo();
  const count = useCount();
  const { me, reload: reloadMe, down } = usePanel();
  const [n, setN] = useState(0);
  const remote = useLoad(() => panel<{ kinds: WorkspacePart[] }>('workspace').then((r) => r.kinds), [n]);
  const [auto, setAuto] = useState(() => Boolean(readMemo().auto));
  const [busy, setBusy] = useState(false);
  const [results, setResults] = useState<SyncResult[] | null>(null);
  const [pulled, setPulled] = useState(false);
  const [open, setOpen] = useState<Kind | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const versions = useLoad(() => (open ? panel<{ versions: Version[] }>(`workspace/${open}/versions`).then((r) => r.versions) : Promise.resolve([] as Version[])), [open, n]);

  const memo = readMemo();
  const local = useMemo(() => Object.fromEntries(KINDS.map((k) => [k, collect(localStorage, k)])), [n, results]); // eslint-disable-line react-hooks/exhaustive-deps

  const run = async (resolve?: Partial<Record<Kind, 'local' | 'cloud'>>) => {
    setBusy(true); setMsg(null);
    try {
      const r = await syncWorkspace({ resolve });
      setResults(r.results); setPulled((p) => p || r.pulled);
      setN((x) => x + 1); reloadMe();
    } catch (e) {
      setMsg(e instanceof HttpError && e.status === 429 ? L('کمی صبر کنید و دوباره بزنید.', 'Wait a moment and try again.') : L('همگام‌سازی انجام نشد؛ سرور پنل در دسترس نیست.', 'Sync failed; the panel server is not reachable.'));
    } finally { setBusy(false); }
  };

  const toggleAuto = async (on: boolean) => {
    setAuto(on);
    writeMemo({ ...readMemo(), auto: on });
    await savePrefs({ autosync: on }).catch(() => null);
  };

  const restore = async (kind: Kind, version: number) => {
    if (!window.confirm(L(`نسخه‌ی ${count(version)} از «${kindLabel(kind, L)}» در این مرورگر جایگزین شود؟ نسخه‌ی فعلی ابر در تاریخچه می‌ماند.`, `Replace «${kindLabel(kind, L)}» in this browser with version ${version}? The current cloud version stays in the history.`))) return;
    setBusy(true);
    try { await restoreVersion(kind, version); setPulled(true); setN((x) => x + 1); } catch { setMsg(L('بازگردانی انجام نشد.', 'Restore failed.')); } finally { setBusy(false); }
  };

  const conflicts = (results ?? []).filter((r) => r.action === 'conflict');
  const state = (k: Kind, part?: WorkspacePart): [string, Tone] => {
    const action = plan(local[k]!, part?.version ?? 0, memo.kinds[k]);
    if (action === 'none') return part?.version ? [L('همگام', 'In sync'), 'good'] : [L('خالی', 'Empty'), 'muted'];
    if (action === 'push') return [L('تغییر در این مرورگر', 'Changed here'), 'info'];
    if (action === 'pull') return [L('نسخه‌ی تازه‌تر در ابر', 'Newer in the cloud'), 'gold'];
    return [L('تغییر در هر دو', 'Changed in both'), 'warn'];
  };

  return (
    <Section title={L('فضای کار ابری', 'Cloud workspace')} sub={L('چیدمان‌ها، تنظیمات چارت، ترسیم‌ها، اندیکاتورها و دیده‌بان‌ها روی حساب شما؛ هر مرورگری که وارد شوید همان را می‌گیرد. ده نسخه‌ی آخر هر بخش برای بازگردانی نگه داشته می‌شود.', 'Layouts, chart settings, drawings, indicators and watchlists on your account; every browser you sign in to gets the same. The last ten versions of each part are kept for restore.')}
      actions={<button className="btn btn-primary sm" disabled={busy || Boolean(down)} onClick={() => void run()}>{busy ? L('در حال همگام‌سازی…', 'Syncing…') : L('همگام‌سازی همین حالا', 'Sync now')}</button>}>
      {msg ? <Note tone="bad" icon="ph-warning">{msg}</Note> : null}
      {pulled ? <Note tone="gold" icon="ph-cloud-arrow-down">{L('داده‌های تازه از ابر رسید. برای دیدن آن‌ها صفحه را دوباره بارگذاری کنید.', 'New data arrived from the cloud. Reload to see it.')} <button className="ucp-link" onClick={() => window.location.reload()}>{L('بارگذاری دوباره', 'Reload')}</button></Note> : null}
      {conflicts.length ? (
        <Note tone="warn" icon="ph-warning">
          <p>{L('این بخش‌ها هم در این مرورگر و هم در ابر (از دستگاهی دیگر) تغییر کرده‌اند. کدام بماند؟', 'These parts changed both here and in the cloud (from another device). Which one stays?')}</p>
          <ul className="ucp-conflicts">{conflicts.map((c) => (
            <li key={c.kind}><b>{kindLabel(c.kind, L)}</b>
              <button className="btn btn-quiet sm" disabled={busy} onClick={() => void run({ [c.kind]: 'local' })}>{L('این مرورگر', 'This browser')}</button>
              <button className="btn btn-quiet sm" disabled={busy} onClick={() => void run({ [c.kind]: 'cloud' })}>{L('نسخه‌ی ابر', 'The cloud')}</button></li>
          ))}</ul>
        </Note>
      ) : null}

      <Card>
        <div className="ucp-row">
          <div className="ucp-row-label"><span>{L('همگام‌سازی خودکار', 'Automatic sync')}</span><small>{L('هر دو دقیقه و هنگام بستن زبانه، تغییرات این مرورگر به ابر می‌رود؛ چیزی بدون اجازه‌ی شما در این مرورگر عوض نمی‌شود.', 'Every two minutes and when the tab is hidden, this browser’s changes go to the cloud; nothing here is replaced without you.')}</small></div>
          <div className="ucp-row-value"><Switch on={auto} label={L('همگام‌سازی خودکار', 'Automatic sync')} onChange={(v) => void toggleAuto(v)} /></div>
        </div>
        <div className="ucp-row">
          <div className="ucp-row-label"><span>{L('فایل فضای کار', 'Workspace file')}</span><small>{L('همه‌ی بخش‌ها در یک فایل، برای نگه‌داری یا بردن به مرورگری بدون حساب.', 'Every part in one file, to keep or to take to a browser without an account.')}</small></div>
          <div className="ucp-row-value ucp-actions">
            <button className="btn btn-quiet sm" onClick={() => downloadText(`pro-chart-workspace-${new Date().toISOString().slice(0, 10)}.json`, exportWorkspace())}>{L('خروجی', 'Export')}</button>
            <button className="btn btn-quiet sm" onClick={() => void pickText().then((text) => { if (!text) return; try { const k = importWorkspace(text); setMsg(null); setPulled(true); setN((x) => x + 1); window.alert(L(`${count(k)} مورد وارد شد.`, `${k} items imported.`)); } catch { setMsg(L('این فایل فضای کار پرو چارت نیست.', 'That is not a Pro Chart workspace file.')); } })}>{L('ورود از فایل', 'Import')}</button>
          </div>
        </div>
      </Card>

      <div className="ucp-parts">
        {KINDS.map((k) => {
          const part = (remote.data ?? me?.workspace ?? []).find((w) => w.kind === k);
          const [label, tone] = state(k, part);
          const size = blobBytes(local[k]!);
          const keys = Object.keys(local[k]!.keys).length;
          const result = results?.find((r) => r.kind === k);
          return (
            <Card key={k} className="ucp-part" title={kindLabel(k, L)} actions={<Pill tone={tone}>{label}</Pill>}>
              <dl className="ucp-kv">
                <dt>{L('این مرورگر', 'This browser')}</dt><dd>{keys ? <><span className="num">{bytes(size)}</span> · {L(`${count(keys)} کلید`, `${keys} keys`)}</> : L('خالی', 'Empty')}</dd>
                <dt>{L('ابر', 'Cloud')}</dt><dd>{part?.version ? <><span className="num">v{part.version} · {bytes(part.size)}</span> · {ago((part.updated_at ?? 0) * 1000)}</> : '—'}</dd>
                {part?.device ? <><dt>{L('از', 'From')}</dt><dd>{part.device}</dd></> : null}
              </dl>
              {part ? <div className="ucp-meter" aria-label={L('حجم استفاده‌شده', 'Space used')}><i style={{ width: `${Math.min(100, (Math.max(size, part.size) / part.limit) * 100)}%` }} /></div> : null}
              {result?.action === 'too_large' ? <p className="ucp-bad-text">{L('این بخش از سقف حجم ابر بزرگ‌تر است.', 'This part is larger than the cloud allows.')}</p> : null}
              {result?.action === 'failed' ? <p className="ucp-bad-text">{L('این بخش همگام نشد.', 'This part did not sync.')}</p> : null}
              <div className="ucp-actions">
                <button className="btn btn-quiet sm" disabled={!part?.versions} onClick={() => setOpen(open === k ? null : k)}>{L(`نسخه‌ها (${count(part?.versions ?? 0)})`, `Versions (${part?.versions ?? 0})`)}</button>
              </div>
              {open === k ? (
                <ul className="ucp-versions">
                  {(versions.data ?? []).map((v) => (
                    <li key={v.version}>
                      <span className="num">v{v.version}</span><span className="num">{stamp(v.saved_at * 1000)}</span><span className="ucp-muted">{v.device ?? ''}{v.restored_from ? ` · ${L('از', 'from')} v${v.restored_from}` : ''}</span><span className="num ucp-muted">{bytes(v.size)}</span>
                      {v.version === part?.version && hashBlob(local[k]!) === memo.kinds[k]?.hash ? <Pill tone="good">{L('جاری', 'Current')}</Pill> : <button className="btn btn-quiet sm" disabled={busy} onClick={() => void restore(k, v.version)}>{L('بازگردانی', 'Restore')}</button>}
                    </li>
                  ))}
                </ul>
              ) : null}
            </Card>
          );
        })}
      </div>
      <Library />
    </Section>
  );
}
