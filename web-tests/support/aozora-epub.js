// @ts-check
/**
 * Converts Aozora Bunko text (already decoded to a string) to an EPUB, keeping furigana as
 * <ruby>. Used to make the sample book in docs/samples; enough for Aozora's basic markup.
 */
const { buildEpub } = require('./epub');

const KANJI = '[\\u3400-\\u9fff\\uf900-\\ufaff々〆ヶ〇]';

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** One line of Aozora text to XHTML: ｜base《reading》 and kanji《reading》 become ruby. */
function lineToXhtml(line) {
  return escapeHtml(line)
    .replace(/［＃[^］]*］/g, '')
    .replace(/｜([^《｜]+)《([^》]+)》/g, '<ruby>$1<rt>$2</rt></ruby>')
    .replace(new RegExp(`(${KANJI}+)《([^》]+)》`, 'g'), '<ruby>$1<rt>$2</rt></ruby>');
}

/**
 * @param {string} text Aozora Bunko text
 * @param {{ title: string, maxParagraphs?: number, attribution: string[] }} options
 */
function aozoraToEpub(text, { title, maxParagraphs = Infinity, attribution }) {
  const lines = text.split(/\r?\n/);
  // Skip the title block and the notation guide between the two dashed lines.
  const dashes = lines.reduce((found, line, i) => (/^-{20,}$/.test(line) ? [...found, i] : found), []);
  const body = lines.slice(dashes.length >= 2 ? dashes[1] + 1 : 0);
  const paragraphs = [];
  for (const line of body) {
    if (paragraphs.length >= maxParagraphs) break;
    if (!line.trim()) continue;
    const heading = line.match(/^［＃[^］]*］(.+?)［＃「.+?」は(大|中|小)見出し］$/);
    paragraphs.push(heading ? `<h2>${escapeHtml(heading[1])}</h2>` : `<p>${lineToXhtml(line)}</p>`);
  }
  paragraphs.push(...attribution.map((line) => `<p class="attribution">${escapeHtml(line)}</p>`));
  return buildEpub({ title, paragraphs, raw: true });
}

module.exports = { aozoraToEpub, lineToXhtml };
