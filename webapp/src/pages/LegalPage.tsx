import { usePage } from '../state/page';
import { usePrefs } from '../state/prefs';
import { Markdown } from './Markdown';
import { State, Tabs, useL, useLoad } from './kit';

// The terms of use and the privacy policy, the same documents the phone shows (docs/legal), with
// the Persian text governing. The English terms exist; the privacy policy is Persian only.
const BASE = `${import.meta.env.BASE_URL}generated/legal/`;

export default function LegalPage() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const page = usePage((s) => s.page);
  const open = usePage((s) => s.open);
  const doc = page === 'privacy' ? 'PRIVACY_POLICY.md' : lang === 'en' ? 'TERMS_EN.md' : 'TERMS.md';
  const { data, error, loading, reload } = useLoad(() => fetch(BASE + doc).then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.text(); }), [doc]);
  return (
    <div className="page-inner narrow legal">
      <Tabs value={page === 'privacy' ? 'privacy' : 'terms'} onChange={(k) => open(k)} tabs={[{ id: 'terms', label: L('قوانین استفاده', 'Terms of use') }, { id: 'privacy', label: L('حریم خصوصی', 'Privacy') }]} />
      <State loading={loading} error={error} onRetry={reload}>{data ? <Markdown source={data} /> : null}</State>
    </div>
  );
}
