import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles/tokens.css';
import './styles/base.css';
import './shell/workspace.css';
import './shell/panels.css';
import { App } from './App';
import { loadIconSprite } from './ui/Icon';
import { loadLogoMap } from './ui/SymbolLogo';
import { loadNames } from './data/names';
import { startPrices } from './data/prices';
import { startWatchlistSync } from './data/watchlistSync';
import { applyAddress } from './route';
import { applyTheme, usePrefs } from './state/prefs';

const BASE = import.meta.env.BASE_URL;
applyTheme(usePrefs.getState().theme);
applyAddress();
startPrices();
startWatchlistSync();

// The sprite, the logo map and the names are small and needed by the first frame; wait for them
// (never longer than 1.5 s) so the screen does not paint twice.
const ready = Promise.all([loadIconSprite(BASE), loadLogoMap(), loadNames(BASE)]);
const cap = new Promise((r) => setTimeout(r, 1500));

void Promise.race([ready, cap]).then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
