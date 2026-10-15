import { useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { academy, viaRelay } from '../data/academy';
import { num, str } from '../data/member';
import { Markdown } from './Markdown';
import { State, errorText, useL, useLoad } from './kit';
import type { PageProps } from './registry';

// One lesson (feature/academy): the text, the diagram and the video, then its quiz — answers
// submitted, each question marked with the right answer and the explanation — and the lesson
// recorded as done with the score.
export default function LessonPage({ param }: PageProps) {
  const L = useL();
  const t = useT();
  const open = usePage((s) => s.open);
  const slug = param ?? '';
  const lesson = useLoad(() => academy<Record<string, unknown>>(`lesson/${encodeURIComponent(slug)}`), [slug]);
  const [quiz, setQuiz] = useState<{ id: string; question: string; options: string[] }[] | null>(null);
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const x = lesson.data;

  const loadQuiz = async () => {
    setBusy(true); setMsg(null);
    try { const r = await academy<{ questions?: { id: string | number; question: string; options: string[] }[] }>(`lesson/${encodeURIComponent(slug)}/quiz`); setQuiz((r.questions ?? []).map((q) => ({ ...q, id: String(q.id) }))); setAnswers({}); setResult(null); }
    catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };
  const submit = async () => {
    setBusy(true); setMsg(null);
    try {
      const r = await academy<Record<string, unknown>>(`lesson/${encodeURIComponent(slug)}/quiz/submit`, { body: { answers } });
      setResult(r);
      await academy(`progress/${encodeURIComponent(slug)}`, { body: { quiz_score: num(r.score) } }).catch(() => null);
    } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };
  const done = async () => { await academy(`progress/${encodeURIComponent(slug)}`, { body: { quiz_score: null } }).then(() => setMsg(L('درس ثبت شد.', 'Lesson recorded.'))).catch((e) => setMsg(errorText(e, t))); };
  const results = new Map(((result?.results ?? []) as Record<string, unknown>[]).map((r) => [String(r.id), r]));

  return (
    <div className="page-inner narrow lesson">
      <button className="link-btn" onClick={() => open('academy')}>← {L('آکادمی', 'Academy')}</button>
      <State loading={lesson.loading} error={lesson.error} onRetry={lesson.reload}>
        {x ? (
          <article>
            <h2 className="fa" dir="auto">{str(x.title)}</h2>
            <p className="muted small fa">{num(x.reading_time_min) ? L(`${num(x.reading_time_min)} دقیقه مطالعه`, `${num(x.reading_time_min)} min read`) : ''}</p>
            {x.summary ? <p className="lead fa" dir="auto">{str(x.summary)}</p> : null}
            {x.video_url ? <video className="lesson-video" src={viaRelay(str(x.video_url), false)} controls preload="metadata" /> : null}
            {x.diagram_image ? <img className="lesson-diagram" src={viaRelay(str(x.diagram_image))} alt="" loading="lazy" /> : null}
            <Markdown source={str(x.content)} />
            <div className="row">
              <button className="btn btn-primary sm" disabled={busy} onClick={() => void loadQuiz()}>{L('آزمون این درس', "This lesson's quiz")}</button>
              <button className="btn btn-quiet sm" onClick={() => void done()}>{L('خواندم', 'Mark as read')}</button>
            </div>
          </article>
        ) : null}
      </State>
      {msg ? <p className="fa">{msg}</p> : null}
      {quiz ? (
        <section className="card quiz">
          {quiz.map((q, qi) => {
            const r = results.get(q.id);
            return (
              <fieldset key={q.id}>
                <legend className="fa" dir="auto">{qi + 1}. {q.question}</legend>
                {q.options.map((o, i) => {
                  const right = r && Number(r.correct_index) === i; const mine = answers[q.id] === i;
                  return <label key={i} className={`opt${right ? ' up' : ''}${r && mine && !right ? ' down' : ''}`}><input type="radio" name={q.id} disabled={Boolean(result)} checked={mine} onChange={() => setAnswers({ ...answers, [q.id]: i })} /> <span className="fa" dir="auto">{o}</span></label>;
                })}
                {r?.explanation ? <p className="muted small fa" dir="auto">{str(r.explanation)}</p> : null}
              </fieldset>
            );
          })}
          {result ? (
            <p className={`fa ${result.passed ? 'up' : 'down'}`}><b>{L('نمره', 'Score')}: <span className="num">{str(result.correct)}/{str(result.total)} · {str(result.score)}%</span></b> {result.passed ? L('قبول شدید.', 'Passed.') : L('دوباره تلاش کنید.', 'Try again.')}</p>
          ) : <button className="btn btn-primary" disabled={busy || Object.keys(answers).length < quiz.length} onClick={() => void submit()}>{L('ثبت پاسخ‌ها', 'Submit the answers')}</button>}
          {result && !result.passed ? <button className="btn btn-quiet sm" onClick={() => void loadQuiz()}>{L('آزمون دوباره', 'Retake')}</button> : null}
        </section>
      ) : null}
    </div>
  );
}
