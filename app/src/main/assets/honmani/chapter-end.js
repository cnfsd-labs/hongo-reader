/*
 * Hides empty paragraphs at the end of a chapter, injected at document start into the ッツ Ebook
 * Reader (ttu) WebView.
 *
 * Many books end a chapter with blank lines (<p><br/></p>): Aozora Bunko texts, Honmani's sample
 * books, publishers' EPUBs. In ttu's page mode a blank line that no longer fits on the chapter's
 * last page starts a page of its own, which shows nothing: a black page between two chapters
 * that looks as if the book had not loaded. ttu turns to the next chapter once the page turn
 * passes the end of what it laid out (the chapter's scroll size, read at every turn), so with
 * the blank lines hidden the next turn goes straight to the next chapter.
 *
 * Only blocks without characters or pictures are hidden. ttu counts characters from the text of
 * the document (hidden or not), so reading positions, progress and sync stay the same.
 */
(() => {
  'use strict';

  if (window.__honmaniChapterEnd) return;
  window.__honmaniChapterEnd = true;

  /** Each chapter's body, as ttu renders it. */
  const CHAPTER_SELECTOR = '.ttu-book-body-wrapper';
  const MARK = 'data-honmani-blank-end';
  const VISIBLE_SELECTOR = 'img, svg, image, video, audio, canvas, iframe, object, embed, hr, table, math';
  /** Containers a chapter's last blank lines may sit in (EPUBs often wrap the text in a <div>). */
  const CONTAINERS = new Set(['DIV', 'SECTION', 'ARTICLE', 'MAIN']);

  const done = new WeakSet();

  function isBlank(element) {
    return !(element.textContent || '').trim() && element.querySelector(VISIBLE_SELECTOR) === null &&
      !element.matches(VISIBLE_SELECTOR);
  }

  /** Hides the blank blocks at the end of `parent`, looking into its last container. */
  function trim(parent) {
    for (let child = parent.lastElementChild; child; child = child.previousElementSibling) {
      if (isBlank(child)) {
        child.setAttribute(MARK, '');
        child.style.setProperty('display', 'none', 'important');
        continue;
      }
      if (CONTAINERS.has(child.tagName)) trim(child);
      return;
    }
  }

  function check() {
    for (const chapter of document.querySelectorAll(CHAPTER_SELECTOR)) {
      if (done.has(chapter)) continue;
      done.add(chapter);
      trim(chapter);
    }
  }

  // Runs right after ttu puts a chapter in, before the page is drawn.
  const observer = new MutationObserver(check);
  const watch = () => {
    observer.observe(document.documentElement, { childList: true, subtree: true });
    check();
  };
  if (document.documentElement) watch();
  else document.addEventListener('DOMContentLoaded', watch, { once: true });
})();
