import { useEffect, useState } from 'react';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { HttpError } from '../data/http';
import {
  authMethods, forgotPassword, googleIdToken, registerStart, registerVerify, signIn, signInWithGoogle, type AuthMethods,
} from '../data/auth';
import type { StringKey } from '../i18n/strings';

type Step = 'signin' | 'signup' | 'verify' | 'forgot' | 'forgotSent';

// TradingView's sign-in sheet: Google first, then email; one dialog for sign-in, sign-up with the
// emailed code, and the forgotten password. Errors in the backend's own Persian words when it gives
// them, otherwise ours.
export function SignIn({ onClose }: { onClose(): void }) {
  const t = useT();
  const [step, setStep] = useState<Step>('signin');
  const [methods, setMethods] = useState<AuthMethods | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [regToken, setRegToken] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => { void authMethods().then(setMethods); }, []);
  useEffect(() => { setError(null); }, [step]);

  const fail = (e: unknown, fallback: StringKey) => {
    if (e instanceof HttpError) {
      if (e.readerMessage) return setError(e.readerMessage);
      if (e.status === 429) return setError(t('tooMany'));
      if (e.status === -1) return setError(t('networkError'));
    }
    if (e instanceof Error && e.message === 'popup-blocked') return setError(t('popupBlocked'));
    setError(t(fallback));
  };

  const run = async (fn: () => Promise<void>, fallback: StringKey) => {
    setBusy(true); setError(null);
    try { await fn(); } catch (e) { fail(e, fallback); } finally { setBusy(false); }
  };

  const google = () => run(async () => {
    if (!methods?.googleClientId) throw new Error('no-google');
    const token = await googleIdToken(methods.googleClientId);
    await signInWithGoogle(token);
    onClose();
  }, 'networkError');

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (step === 'signin') void run(async () => { await signIn(email, password); onClose(); }, 'wrongCredentials');
    else if (step === 'signup') void run(async () => { const r = await registerStart(email, password, name); setRegToken(r.token); setStep('verify'); }, 'networkError');
    else if (step === 'verify') void run(async () => { await registerVerify(regToken, code); onClose(); }, 'networkError');
    else if (step === 'forgot') void run(async () => { await forgotPassword(email); setStep('forgotSent'); }, 'networkError');
  };

  const title = step === 'signup' || step === 'verify' ? t('signUpTitle') : step === 'signin' ? t('signInTitle') : t('forgotPassword');

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog auth" role="dialog" aria-modal="true" aria-label={title} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
        <header className="dialog-head">
          <h2 className="fa">{title}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <form className="auth-body" onSubmit={submit} dir="rtl">
          {step === 'signin' || step === 'signup' ? (
            <>
              {methods?.google && methods.googleClientId ? (
                <>
                  <button type="button" className="btn btn-google" onClick={google} disabled={busy}>
                    <Icon name="ph-google-logo" size="md" /><span>{t('continueGoogle')}</span>
                  </button>
                  <div className="or"><span>{t('orEmail')}</span></div>
                </>
              ) : null}
              {step === 'signup' ? (
                <label className="field"><span>{t('fullName')}</span>
                  <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required minLength={2} />
                </label>
              ) : null}
              <label className="field"><span>{t('email')}</span>
                <input type="email" dir="ltr" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
              </label>
              <label className="field"><span>{t('password')}</span>
                <input type="password" dir="ltr" value={password} onChange={(e) => setPassword(e.target.value)}
                  autoComplete={step === 'signup' ? 'new-password' : 'current-password'} required minLength={step === 'signup' ? 8 : 1} />
              </label>
            </>
          ) : null}
          {step === 'verify' ? (
            <>
              <p className="hint">{t('codeSentTo')} <span className="num">{email}</span></p>
              <label className="field"><span>{t('code')}</span>
                <input dir="ltr" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} autoComplete="one-time-code" required className="num code" />
              </label>
            </>
          ) : null}
          {step === 'forgot' ? (
            <label className="field"><span>{t('email')}</span>
              <input type="email" dir="ltr" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
            </label>
          ) : null}
          {step === 'forgotSent' ? <p className="hint">{t('resetSent')}</p> : null}

          {error ? <p className="error" role="alert">{error}</p> : null}

          {step !== 'forgotSent' ? (
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {step === 'signin' ? t('signIn') : step === 'signup' ? t('signUpTitle') : step === 'verify' ? t('verify') : t('sendResetLink')}
            </button>
          ) : null}

          <div className="auth-links">
            {step === 'signin' ? (
              <>
                <button type="button" className="link-btn" onClick={() => setStep('forgot')}>{t('forgotPassword')}</button>
                <button type="button" className="link-btn" onClick={() => setStep('signup')}>{t('createAccount')}</button>
              </>
            ) : (
              <button type="button" className="link-btn" onClick={() => setStep('signin')}>{step === 'signup' ? t('haveAccount') : t('back')}</button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
