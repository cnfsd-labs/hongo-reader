/*
 * Shows ttu's help texts (the "?" icons in its settings and statistics) at the bottom of the
 * screen. ttu places them next to the icon with Popper, which in the Android WebView ends up off
 * screen for icons further down the page (it vanishes when scrolling down and comes back when
 * scrolling up). A fixed place can't be wrong. Only text popovers are moved; ttu marks those with
 * `whitespace-pre-wrap`, so menus such as the font list keep their place.
 */
(() => {
  'use strict';

  const STYLE_ID = 'honmani-help-text';
  const CSS = `
    div[data-popover].absolute.whitespace-pre-wrap {
      position: fixed !important;
      inset: auto 16px calc(24px + env(safe-area-inset-bottom)) 16px !important;
      transform: none !important;
      margin: 0 !important;
      max-width: none !important;
      z-index: 1000 !important;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
    }
  `;

  function addStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = CSS;
    (document.head || document.documentElement).appendChild(style);
  }

  if (document.documentElement) addStyle();
  else document.addEventListener('DOMContentLoaded', addStyle);
})();
