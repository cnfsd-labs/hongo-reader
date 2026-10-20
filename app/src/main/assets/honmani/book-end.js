/*
 * Tells Honmani when the end of a book is on screen, injected at document start into the
 * ッツ Ebook Reader (ttu) WebView.
 *
 * ttu's progress is where the current page starts, so a book read to its last page stays below
 * 100%. When the last character of the book is on screen, this posts
 * { type: 'library.finished', id } (ttu's book id from the address), once per book and page load;
 * the library then counts the book as finished.
 *
 * Books were sometimes marked finished on their first pages, with the last character reported on
 * screen (cause not reproduced). So ttu's own count must agree: its page change events carry
 * the characters before the current page and in the whole book, and at most a page's worth
 * (MAX_PAGE_CHARS) may be left. A refused end is logged for "Share diagnostics".
 */
(() => {
  'use strict';

  if (window.__honmaniBookEnd) return;
  window.__honmaniBookEnd = true;

  const BOOK_ROOT_SELECTOR = '.book-content';
  const SKIPPED_SELECTOR = 'rt, rp, script, style';
  const CHECK_MS = 400;
  /** ttu's own event, dispatched on document when the reading position changes. */
  const PAGE_CHANGE = 'ttsu:page.change';
  /** More characters than any one page shows, even with small text on a tablet. */
  const MAX_PAGE_CHARS = 3000;

  let reported = null;
  let timer = 0;
  /** ttu's counts from its page change events; null until ttu has sent them. */
  let explored = null;
  let total = null;
  let refusedLogged = null;

  function bookId() {
    if (!location.pathname.startsWith('/b')) return null;
    const id = Number(new URLSearchParams(location.search).get('id'));
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  function onScreen(rect) {
    return rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.left < innerWidth &&
      rect.bottom > 0 && rect.top < innerHeight;
  }

  /** The last character of book text that isn't blank (furigana skipped): { node, offset }. */
  function lastChar(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: (node) =>
        node.parentElement && node.parentElement.closest(SKIPPED_SELECTOR)
          ? NodeFilter.FILTER_REJECT
          : NodeFilter.FILTER_ACCEPT,
    });
    let last = null;
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      if (node.data.trim()) last = node;
    }
    if (!last) return null;
    let offset = last.data.length - 1;
    while (offset > 0 && !last.data[offset].trim()) offset--;
    return { node: last, offset };
  }

  function endOnScreen() {
    const root = document.querySelector(BOOK_ROOT_SELECTOR);
    if (!root) return false;
    const end = lastChar(root);
    if (!end) return false;
    const range = document.createRange();
    range.setStart(end.node, end.offset);
    range.setEnd(end.node, end.offset + 1);
    return Array.from(range.getClientRects()).some(onScreen);
  }

  /** Whether ttu's counts allow the end to be on this page (unknown counts don't block). */
  function nearEndByCount() {
    if (explored === null || total === null || total <= 0) return true;
    return total - explored <= MAX_PAGE_CHARS;
  }

  function check() {
    const id = bookId();
    if (id === null || id === reported) return;
    if (!endOnScreen()) return;
    if (!nearEndByCount()) {
      if (refusedLogged !== explored) {
        refusedLogged = explored;
        console.log(`honmani book end on screen but ${total - explored} of ${total} characters left`);
      }
      return;
    }
    reported = id;
    if (window.honmani) window.honmani.postMessage(JSON.stringify({ type: 'library.finished', id }));
  }

  function checkLater() {
    clearTimeout(timer);
    timer = setTimeout(check, CHECK_MS);
  }

  let countsFor = null;
  document.addEventListener(PAGE_CHANGE, (event) => {
    // Books open inside the running page, so counts from the book before must not carry over.
    if (countsFor !== bookId()) {
      countsFor = bookId();
      explored = null;
      total = null;
    }
    const detail = event.detail || {};
    if (typeof detail.exploredCharCount === 'number') explored = detail.exploredCharCount;
    if (typeof detail.bookCharCount === 'number') total = detail.bookCharCount;
    checkLater();
  });
  for (const type of ['scroll', 'keyup', 'click', 'touchend', 'wheel']) {
    window.addEventListener(type, checkLater, { capture: true, passive: true });
  }
  window.addEventListener('resize', checkLater);
  window.addEventListener('load', () => setTimeout(check, 1500));
})();
