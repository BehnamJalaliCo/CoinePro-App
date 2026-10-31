// Everything that teaches the app without being asked: the first run, the coach and the chrome's
// tooltips. Mounted once, by the app.
import { FirstRun } from '../firstrun/FirstRun';
import { CoachLayer } from './Coach';
import { ChromeTips } from '../shell/ChromeTips';

export function TeachingLayer({ onSignIn }: { onSignIn(): void }) {
  return (
    <>
      <FirstRun onSignIn={onSignIn} />
      <CoachLayer />
      <ChromeTips />
    </>
  );
}
