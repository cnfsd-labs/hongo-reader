/*
 * Honmani tap bridge, injected at document start into the ッツ Ebook Reader WebView.
 *
 * Messages to the app, through the `honmani` object registered with
 * WebViewCompat.addWebMessageListener:
 *
 *   { "type": "lookup", "text": "<up to SCAN_LENGTH chars from the tapped char>",
 *     "sentence": "<sentence around it>", "sentenceOffset": <index of text in sentence>,
 *     "ruby": [{ "start": <n>, "end": <n>, "reading": "<furigana>" }] }
 *
 * "ruby" lists furigana for ruby bases that lie wholly inside "text" (indexes into "text"); it is
 * left out when there are none.
 *   { "type": "dismiss" }   (a tap on book content that is not on text)
 *
 * Furigana (<rt>, <rp>) is not part of the scanned text; a tap on furigana scans from the start
 * of the base text it reads. Scanning stops at the end of the
 * tapped paragraph and at a line break (<br>). A tap on an opening bracket or quote scans from
 * the character after it.
 *
 * Messages from the app (replies on the same object):
 *
 *   { "type": "highlight", "length": <n>, "color": "#rrggbb" }
 *                                             marks n characters from the last tapped one, in
 *                                             the app's accent colour (optional)
 *   { "type": "clear" }                      removes the mark
 *
 * The mark uses the CSS Custom Highlight API, so ttu's DOM is never modified. WebViews before
 * Chromium 105 lack it; there the mark is drawn as boxes in an overlay outside ttu's content,
 * redrawn when the page scrolls (ttu turns pages by scrolling) or resizes.
 */
(() => {
  'use strict';

  if (window.__honmaniTapBridge) return;
  window.__honmaniTapBridge = true;

  const SCAN_LENGTH = 20;
  /** A tap moves at most this far (CSS px) and lasts at most this long (ms). */
  const TAP_SLOP = 10;
  const TAP_TIME = 500;
  /** How long a tap's click may take before the tap is looked up without it (ms). */
  const CLICK_WAIT = 350;
  const MAX_READING_LENGTH = 32;
  const MAX_SENTENCE_LENGTH = 400;
  const BOOK_ROOT_SELECTOR = '.book-content';
  const SKIPPED_SELECTOR = 'rt, rp, script, style';
  const INTERACTIVE_SELECTOR = 'a, button, input, textarea, select, [role="button"]';
  const SENTENCE_END = '。！？!?…\n';
  const CLOSING_QUOTES = '」』）〉》】"\'';
  const OPENING_BRACKETS = '「『（(〈《【〔［｛“‘〝"\'';
  const HIGHLIGHT_NAME = 'honmani-lookup';
  const OVERLAY_ID = 'honmani-highlight-overlay';

  /**
   * Where the last lookup started: { block, node, index, skip }, `skip` being the opening brackets
   * between the tapped character and the scanned text.
   */
  let lastTap = null;

  /** The fallback mark: { ranges, background }, or null. */
  let overlayMark = null;

  function hasHighlightApi() {
    return Boolean(window.CSS && CSS.highlights && window.Highlight);
  }

  function isSkipped(node) {
    const parent = node.parentElement;
    return !parent || parent.closest(SKIPPED_SELECTOR) !== null;
  }

  function caretAt(x, y) {
    if (document.caretPositionFromPoint) {
      const position = document.caretPositionFromPoint(x, y);
      return position && { node: position.offsetNode, offset: position.offset };
    }
    const range = document.caretRangeFromPoint && document.caretRangeFromPoint(x, y);
    return range && { node: range.startContainer, offset: range.startOffset };
  }

  /**
   * Boxes of one character. getBoundingClientRect() is not used because, for the first
   * character of a wrapped line, Chromium adds an empty box at the end of the previous line.
   */
  function charRects(node, index) {
    const range = document.createRange();
    range.setStart(node, index);
    range.setEnd(node, index + 1);
    return Array.from(range.getClientRects()).filter((rect) => rect.width > 0 && rect.height > 0);
  }

  /** Index of the character in `node` under (x, y), or -1. The caret can sit on either side. */
  function hitCharIndex(node, offset, x, y) {
    if (!node || node.nodeType !== Node.TEXT_NODE || isSkipped(node)) return -1;
    for (const index of [offset, offset - 1]) {
      if (index < 0 || index >= node.data.length) continue;
      const hit = charRects(node, index).some(
        (rect) => x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom,
      );
      if (hit) {
        // Never start in the middle of a surrogate pair.
        const code = node.data.charCodeAt(index);
        return code >= 0xdc00 && code <= 0xdfff && index > 0 ? index - 1 : index;
      }
    }
    return -1;
  }

  function isBlock(element) {
    const display = getComputedStyle(element).display;
    return !display.startsWith('inline') && !display.startsWith('ruby') && display !== 'contents';
  }

  function blockAncestor(node, root) {
    for (let element = node.parentElement; element && element !== root; element = element.parentElement) {
      if (isBlock(element)) return element;
    }
    return root;
  }

  function textWalker(block) {
    return document.createTreeWalker(block, NodeFilter.SHOW_TEXT, {
      acceptNode: (node) => (isSkipped(node) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT),
    });
  }

  /** The <rt> giving the reading of ruby base text `node`, or null. */
  function rubyTextFor(node, block) {
    const ruby = node.parentElement.closest('ruby');
    if (!ruby || !block.contains(ruby)) return null;
    for (const rt of ruby.querySelectorAll('rt')) {
      if (node.compareDocumentPosition(rt) & Node.DOCUMENT_POSITION_FOLLOWING) return rt;
    }
    return null;
  }

  /** Like textWalker, but also stops at <br> elements. */
  function lineWalker(block) {
    return document.createTreeWalker(block, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
      acceptNode: (node) => {
        if (node.nodeType === Node.ELEMENT_NODE) {
          return node.tagName === 'BR' ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
        }
        return isSkipped(node) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      },
    });
  }

  /**
   * Text of `block` without furigana (a <br> as "\n"), the index of (node, index) within it, and
   * the ruby bases in it as { start, end, reading } (indexes into the text).
   */
  function blockText(block, node, index) {
    const walker = lineWalker(block);
    let text = '';
    let position = -1;
    const ruby = [];
    let lastRt = null;
    for (let current = walker.nextNode(); current; current = walker.nextNode()) {
      if (current.nodeType === Node.ELEMENT_NODE) {
        text += '\n';
        lastRt = null;
        continue;
      }
      if (current === node) position = text.length + index;
      const rt = rubyTextFor(current, block);
      if (rt && rt === lastRt) {
        ruby[ruby.length - 1].end += current.data.length;
      } else if (rt) {
        const reading = rt.textContent.trim();
        ruby.push({ start: text.length, end: text.length + current.data.length, reading });
      }
      lastRt = rt;
      text += current.data;
    }
    return { text, position, ruby };
  }

  /** Ruby bases wholly inside text[start, start + length), relative to start. */
  function rubyWithin(ruby, start, length) {
    return ruby
      .map((span) => ({ start: span.start - start, end: span.end - start, reading: span.reading }))
      .filter(
        (span) =>
          span.start >= 0 &&
          span.end <= length &&
          span.end > span.start &&
          span.reading.length > 0 &&
          span.reading.length <= MAX_READING_LENGTH,
      );
  }

  function takeCodePoints(text, count) {
    return Array.from(text).slice(0, count).join('');
  }

  function sentenceAround(text, position) {
    let start = position;
    while (start > 0 && !SENTENCE_END.includes(text[start - 1])) start--;
    let end = position;
    while (end < text.length && !SENTENCE_END.includes(text[end])) end++;
    while (end < text.length && SENTENCE_END.includes(text[end])) end++;
    while (end < text.length && CLOSING_QUOTES.includes(text[end])) end++;

    // Keep very long "sentences" (e.g. no punctuation) to a window around the tap.
    if (end - start > MAX_SENTENCE_LENGTH) {
      start = Math.max(start, position - MAX_SENTENCE_LENGTH / 2);
      end = Math.min(end, start + MAX_SENTENCE_LENGTH);
    }
    let sentence = text.slice(start, end);
    const leading = sentence.length - sentence.trimStart().length;
    sentence = sentence.trim();
    return { sentence, offset: position - start - leading };
  }

  /** A tap on book content that is not on a link, button or similar. */
  function isBookBackground(target) {
    return target.closest(BOOK_ROOT_SELECTOR) !== null && target.closest(INTERACTIVE_SELECTOR) === null;
  }

  /** The text node and index of the character under (x, y), or null. */
  function charAt(x, y) {
    const caret = caretAt(x, y);
    if (!caret) return null;
    const index = hitCharIndex(caret.node, caret.offset, x, y);
    return index < 0 ? null : { node: caret.node, index };
  }

  /**
   * For a tap on furigana, the start of the base text it reads: furigana sits right beside its
   * kanji (above them in vertical text), and a tap there means the word. Null for other taps.
   */
  function rubyBaseOf(target) {
    const rt = target.closest('rt');
    const ruby = rt && rt.closest('ruby');
    if (!ruby) return null;
    // The base is the text since the furigana before this one (東<rt>とう</rt>京<rt>きょう</rt>).
    let first = null;
    const walker = textWalker(ruby);
    for (let current = walker.nextNode(); current; current = walker.nextNode()) {
      if (current.compareDocumentPosition(rt) & Node.DOCUMENT_POSITION_PRECEDING) break;
      const previous = rubyTextFor(current, ruby);
      if (previous !== rt) continue;
      if (!first) first = current;
    }
    return first ? { node: first, index: 0 } : null;
  }

  function lookupAt(x, y, target) {
    const root = target.closest(BOOK_ROOT_SELECTOR);
    if (!root || target.closest(INTERACTIVE_SELECTOR)) return null;

    const hit = rubyBaseOf(target) || charAt(x, y);
    if (!hit || !root.contains(hit.node)) return null;
    const { node, index } = hit;

    const block = blockAncestor(node, root);
    const { text: paragraph, position, ruby: paragraphRuby } = blockText(block, node, index);
    if (position < 0) return null;

    let start = position;
    while (start < paragraph.length && OPENING_BRACKETS.includes(paragraph[start])) start++;
    const text = takeCodePoints(paragraph.slice(start), SCAN_LENGTH).split('\n')[0];
    if (!text.trim() || /^\s/.test(text)) return null;

    const { sentence, offset } = sentenceAround(paragraph, start);
    lastTap = { block, node, index, skip: start - position };
    const message = { type: 'lookup', text, sentence, sentenceOffset: offset };
    const ruby = rubyWithin(paragraphRuby, start, text.length);
    if (ruby.length > 0) message.ruby = ruby;
    return message;
  }

  function clearHighlight() {
    if (hasHighlightApi()) CSS.highlights.delete(HIGHLIGHT_NAME);
    overlayMark = null;
    const overlay = document.getElementById(OVERLAY_ID);
    if (overlay) overlay.remove();
  }

  /** Draws the fallback mark's boxes; it never takes taps, so lookups still reach the book. */
  function drawOverlay() {
    let overlay = document.getElementById(OVERLAY_ID);
    if (!overlayMark) {
      if (overlay) overlay.remove();
      return;
    }
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.id = OVERLAY_ID;
      overlay.style.cssText =
        'position: fixed; inset: 0; pointer-events: none; z-index: 2147483647; overflow: hidden;';
      document.documentElement.appendChild(overlay);
    }
    const boxes = [];
    for (const range of overlayMark.ranges) {
      for (const rect of range.getClientRects()) {
        if (rect.width <= 0 || rect.height <= 0) continue;
        const box = document.createElement('div');
        box.style.cssText =
          `position: absolute; left: ${rect.left}px; top: ${rect.top}px; ` +
          `width: ${rect.width}px; height: ${rect.height}px; ` +
          `background-color: ${overlayMark.background}; border-radius: 2px;`;
        boxes.push(box);
      }
    }
    overlay.replaceChildren(...boxes);
  }

  /** The mark's colour: the app's accent (#rrggbb) at 40% opacity, else amber. */
  function highlightBackground(color) {
    const hex = /^#([0-9a-fA-F]{6})$/.exec(color || '');
    if (!hex) return 'rgba(255, 196, 0, 0.45)';
    const value = parseInt(hex[1], 16);
    return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, 0.4)`;
  }

  function setHighlightStyle(color) {
    let style = document.getElementById('honmani-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'honmani-style';
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = `::highlight(${HIGHLIGHT_NAME}) { background-color: ${highlightBackground(color)}; }`;
  }

  /** Marks `length` characters of book text from the last tap, skipping furigana. */
  function highlight(length, color) {
    clearHighlight();
    if (!lastTap || !lastTap.node.isConnected) return;

    const walker = textWalker(lastTap.block);
    walker.currentNode = lastTap.node;
    const ranges = [];
    let node = lastTap.node;
    let offset = lastTap.index;
    // Past the opening brackets the scan skipped.
    let skip = lastTap.skip;
    while (node && skip > 0) {
      const available = node.data.length - offset;
      if (skip < available) {
        offset += skip;
        skip = 0;
      } else {
        skip -= available;
        node = walker.nextNode();
        offset = 0;
      }
    }
    let remaining = length;
    while (node && remaining > 0) {
      const take = Math.min(remaining, node.data.length - offset);
      if (take > 0) {
        const range = document.createRange();
        range.setStart(node, offset);
        range.setEnd(node, offset + take);
        ranges.push(range);
        remaining -= take;
      }
      node = walker.nextNode();
      offset = 0;
    }
    if (ranges.length === 0) return;
    if (hasHighlightApi()) {
      setHighlightStyle(color);
      CSS.highlights.set(HIGHLIGHT_NAME, new Highlight(...ranges));
    } else {
      overlayMark = { ranges, background: highlightBackground(color) };
      drawOverlay();
    }
  }

  function onAppMessage(event) {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch (error) {
      return;
    }
    if (message.type === 'highlight' && Number.isInteger(message.length)) {
      highlight(message.length, message.color);
    } else if (message.type === 'clear') {
      clearHighlight();
    }
  }

  function post(message) {
    const app = window.honmani;
    if (!app) return;
    // Replies arrive on the same object; set the handler before the first message.
    if (app.onmessage !== onAppMessage) app.onmessage = onAppMessage;
    app.postMessage(JSON.stringify(message));
  }

  function onTap(x, y, target) {
    if (!(target instanceof Element)) return;
    const selection = window.getSelection();
    if (selection && !selection.isCollapsed) return;

    const message = lookupAt(x, y, target);
    if (message) {
      clearHighlight();
      post(message);
    } else if (isBookBackground(target)) {
      clearHighlight();
      post({ type: 'dismiss' });
    }
  }

  // Capture phase on window: ttu cancels clicks on <ruby> (to reveal furigana) with
  // stopImmediatePropagation(), so a bubbling listener would miss taps on ruby words.
  // The event is left untouched so ttu still handles it.
  window.addEventListener(
    'click',
    (event) => {
      clearTimeout(tapWithoutClick);
      tapWithoutClick = undefined;
      onTap(event.clientX, event.clientY, event.target);
    },
    true,
  );

  // Chromium sends no click for a tap that stops a fling, such as one up to a second after a
  // page swipe (seen on a OnePlus 7 Pro): a short, still tap whose click doesn't follow is
  // looked up all the same.
  let tapStart = null;
  let tapWithoutClick;
  window.addEventListener(
    'pointerdown',
    (event) => {
      tapStart = event.isPrimary ? { x: event.clientX, y: event.clientY, time: event.timeStamp } : null;
    },
    true,
  );
  window.addEventListener(
    'pointerup',
    (event) => {
      const start = tapStart;
      tapStart = null;
      if (!start || !event.isPrimary) return;
      const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y);
      if (moved > TAP_SLOP || event.timeStamp - start.time > TAP_TIME) return;
      const { clientX: x, clientY: y, target } = event;
      clearTimeout(tapWithoutClick);
      tapWithoutClick = setTimeout(() => {
        tapWithoutClick = undefined;
        onTap(x, y, target);
      }, CLICK_WAIT);
    },
    true,
  );
  window.addEventListener('pointercancel', () => {
    tapStart = null;
  }, true);

  // The fallback mark follows the text: capture catches scrolls of ttu's inner containers too.
  const redrawOverlay = () => {
    if (overlayMark) drawOverlay();
  };
  window.addEventListener('scroll', redrawOverlay, true);
  window.addEventListener('resize', redrawOverlay);
})();
