import { Fragment, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useOverlay } from '../state/overlay';
import { readChart, type VisionResult } from '../data/ai';
import { chartImage } from '../chart/snapshot';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { SymbolSearch } from '../shell/SymbolSearch';
import { SymbolLogo } from '../ui/SymbolLogo';
import { errorText, useL } from './kit';

// Chart vision (feature/ai-vision): the open chart as drawn, or any chart picture the reader has
// (a screenshot from elsewhere, up to 6 MB), read by the platform's vision model.
export default function VisionPage() {
  const L = useL();
  const t = useT();
  const close = usePage((s) => s.close);
  const chartSymbol = usePrefs((s) => s.symbol);
  const [symbol, setSymbol] = useState(chartSymbol);
  const [image, setImage] = useState<Blob | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [res, setRes] = useState<VisionResult | null>(null);
  const [pick, setPick] = useState(false);
  const use = (b: Blob) => { setImage(b); setPreview((p) => { if (p) URL.revokeObjectURL(p); return URL.createObjectURL(b); }); setRes(null); };

  const fromChart = async () => { const b = await chartImage(); if (b) { setSymbol(chartSymbol); use(b); } };
  const fromFile = () => {
    const input = document.createElement('input'); input.type = 'file'; input.accept = 'image/png,image/jpeg,image/webp';
    input.onchange = () => { const f = input.files?.[0]; if (!f) return; if (f.size > 6 * 1024 * 1024) { setErr(L('تصویر باید کمتر از ۶ مگابایت باشد.', 'The picture must be under 6 MB.')); return; } setErr(null); use(f); };
    input.click();
  };
  const run = async () => {
    if (!image) return;
    setBusy(true); setErr(null); setRes(null);
    try { setRes(await readChart(symbol, image)); } catch (e) { setErr(errorText(e, t)); } finally { setBusy(false); }
  };
  const d = digitsOf(symbol);
  const f = (v: number | null) => (v == null ? '—' : formatPrice(v, d));

  return (
    <div className="page-inner">
      {pick ? <SymbolSearch onClose={() => setPick(false)} onPick={(s) => { setSymbol(s); setPick(false); }} /> : null}
      <div className="split">
        <div className="card form">
          <div className="row">
            <button className="btn btn-quiet sm" onClick={() => void fromChart()}>{L('تصویر چارت باز', 'The open chart')}</button>
            <button className="btn btn-quiet sm" onClick={fromFile}>{L('بارگذاری تصویر', 'Upload a picture')}</button>
          </div>
          <div className="row"><span className="muted fa">{L('نماد تصویر', 'Symbol in the picture')}</span><button className="btn btn-quiet sm" onClick={() => setPick(true)}><SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span></button></div>
          {preview ? <img className="vision-preview" src={preview} alt="" /> : <div className="empty fa">{L('یک تصویر انتخاب کنید.', 'Choose a picture.')}</div>}
          <button className="btn btn-primary" disabled={!image || busy} onClick={() => void run()}>{busy ? `${t('aiReading')}…` : L('تحلیل تصویر', 'Read the picture')}</button>
          {err ? <p className="down fa">{err}</p> : null}
        </div>
        <div className="grow">
          {res ? (
            <div className="stack">
              <div className="row">{res.direction ? <span className={`sig-dir ${res.direction === 'long' ? 'up' : 'down'}`}>{res.direction === 'long' ? 'LONG' : 'SHORT'}</span> : null}{res.confidence != null ? <span className="num muted">{res.confidence}%</span> : null}<span className="muted">{res.assessment}</span>
                <span className="ws-spacer" />
                {res.sl != null || res.targets.length ? <button className="btn btn-primary sm" onClick={() => { usePrefs.getState().setSymbol(symbol); const entry = res.entryLow != null && res.entryHigh != null ? (res.entryLow + res.entryHigh) / 2 : res.entryLow ?? res.entryHigh; useOverlay.getState().show({ symbol, direction: res.direction ?? 'long', entry, sl: res.sl, targets: res.targets, label: 'AI' }); close(); }}>{L('نمایش روی چارت', 'Show on the chart')}</button> : null}
              </div>
              <dl className="kv">
                {res.trendBias ? <><dt className="fa">{L('روند', 'Trend')}</dt><dd dir="auto">{res.trendBias}</dd></> : null}
                {res.structure ? <><dt className="fa">{L('ساختار', 'Structure')}</dt><dd dir="auto">{res.structure}</dd></> : null}
                {res.setup ? <><dt className="fa">{L('ستاپ', 'Setup')}</dt><dd dir="auto">{res.setup}</dd></> : null}
                <dt className="fa">{L('ورود', 'Entry')}</dt><dd className="num">{f(res.entryLow)}{res.entryHigh != null && res.entryHigh !== res.entryLow ? ` – ${f(res.entryHigh)}` : ''}</dd>
                <dt className="fa">{L('حد ضرر', 'Stop')}</dt><dd className="num down">{f(res.sl)}</dd>
                {res.targets.map((x, i) => <Fragment key={i}><dt>TP{i + 1}</dt><dd className="num up">{f(x)}</dd></Fragment>)}
              </dl>
              {res.reasoning ? <section className="card"><p className="fa" dir="auto">{res.reasoning}</p></section> : null}
              {res.risk ? <section className="card warn"><p className="fa" dir="auto">{res.risk}</p></section> : null}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
