// @ts-check
/**
 * The library bridge against the real ッツ Ebook Reader build: HonGO!'s native library hands
 * book files to ttu (served at /hongo-books/ like the app's BookPathHandler), opens them by id,
 * reads reading progress and removes books.
 *
 * Needs the ttu build produced by `./gradlew :app:buildTtu` (or set TTU_DIR).
 */
const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');
const { startTtuServer } = require('../support/ttu-server');
const { buildEpub } = require('../support/epub');

const ASSETS = path.join(__dirname, '../../app/src/main/assets/hongo');
const TTU_DIR = process.env.TTU_DIR ?? path.join(__dirname, '../../app/build/generated/ttu/ttu');
const hasBuild = fs.existsSync(path.join(TTU_DIR, 'index.html'));

if (process.env.CI && !hasBuild) {
  throw new Error(`ttu build not found at ${TTU_DIR}; run ./gradlew :app:buildTtu first`);
}
test.skip(!hasBuild, `ttu build not found at ${TTU_DIR}; run ./gradlew :app:buildTtu`);

let server;
test.beforeAll(async () => {
  server = await startTtuServer(TTU_DIR);
});
test.afterAll(() => server?.close());

const BOOKS = {
  'neko.epub': buildEpub({
    title: '吾輩は猫である',
    paragraphs: ['吾輩は猫である。名前はまだ無い。', 'どこで生れたかとんと見当がつかぬ。'],
  }),
  'long.epub': buildEpub({
    title: '長い本',
    paragraphs: Array.from({ length: 300 }, (_, i) => `第${i + 1}段落。${'吾輩は猫である。'.repeat(20)}`),
  }),
  // A web novel's chapter as one paragraph, longer than many pages.
  'one-paragraph.epub': buildEpub({
    title: '一段落の本',
    paragraphs: [Array.from({ length: 400 }, (_, i) => `これは${i + 1}番目の文です。`).join('')],
  }),
  // Furigana on every word: many text nodes per page.
  'ruby.epub': buildEpub({
    title: 'ルビの本',
    paragraphs: Array.from({ length: 120 }, (_, i) => `第${i + 1}段落。` + '<ruby>吾輩<rt>わがはい</rt></ruby>は<ruby>猫<rt>ねこ</rt></ruby>である。'.repeat(25)),
  }),
  'broken.epub': Buffer.from('PK this is not really a zip'),
};

/** Registers the init scripts as ReaderWebView does and serves library files. */
async function setUp(page) {
  await page.addInitScript(() => {
    window.__messages = [];
    window.hongo = { postMessage: (message) => window.__messages.push(JSON.parse(message)) };
  });
  await page.addInitScript({ path: path.join(ASSETS, 'ttu-defaults.js') });
  await page.addInitScript({ path: path.join(ASSETS, 'library-bridge.js') });
  await page.route('**/hongo-books/*', (route) => {
    const name = new URL(route.request().url()).pathname.split('/').pop();
    const body = BOOKS[name];
    if (body) route.fulfill({ status: 200, body, contentType: 'application/octet-stream' });
    else route.fulfill({ status: 404, body: '' });
  });
}

let requestCount = 0;

/** Calls a bridge command and waits for its answer. */
async function call(page, command, args = {}) {
  const requestId = ++requestCount;
  await page.evaluate(([command, args]) => window.hongoLibrary[command](args), [command, { requestId, ...args }]);
  const handle = await page.waitForFunction(
    (requestId) => window.__messages.find((m) => m.requestId === requestId),
    requestId,
    { timeout: 20_000 },
  );
  return handle.jsonValue();
}

async function ensure(page, fileName, ttuId = null, ttuTitle = null) {
  return call(page, 'ensure', { ttuId, ttuTitle, url: `/hongo-books/${fileName}`, fileName });
}

async function progress(page) {
  const count = await page.evaluate(() => window.__messages.length);
  await page.evaluate(() => window.hongoLibrary.progress());
  const handle = await page.waitForFunction(
    (count) => window.__messages.slice(count).find((m) => m.type === 'library.progress'),
    count,
  );
  return (await handle.jsonValue()).books;
}

test('a new book is imported through ttu and opens by id', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);

  const reply = await ensure(page, 'neko.epub');
  expect(reply).toMatchObject({ type: 'library.ready', title: '吾輩は猫である' });
  expect(typeof reply.id).toBe('number');

  // Opened inside the running page: a full load kept ttu's book list on screen meanwhile.
  await page.evaluate(() => { window.__samePage = true; });
  await page.evaluate((ttuId) => window.hongoLibrary.open({ ttuId }), reply.id);
  await page.waitForURL(new RegExp(`/b\\?id=${reply.id}$`));
  await expect(page.locator('.book-content')).toContainText('名前はまだ無い');
  expect(await page.evaluate(() => window.__samePage)).toBe(true);
});

test('a book ttu already holds is not imported again', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);
  const first = await ensure(page, 'neko.epub');

  await page.reload();
  await page.evaluate(() => {
    window.__puts = 0;
    const put = IDBObjectStore.prototype.put;
    IDBObjectStore.prototype.put = function (...args) {
      if (this.name === 'data') window.__puts++;
      return put.apply(this, args);
    };
  });
  const again = await ensure(page, 'neko.epub', first.id, first.title);

  expect(again).toMatchObject({ type: 'library.ready', id: first.id, title: first.title });
  expect(await page.evaluate(() => window.__puts)).toBe(0);
});

test('a stale id (ttu reset, or another book under that id) imports the book again', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);
  const neko = await ensure(page, 'neko.epub');

  const reply = await ensure(page, 'long.epub', neko.id, '長い本');

  expect(reply).toMatchObject({ type: 'library.ready', title: '長い本' });
  expect(reply.id).not.toBe(neko.id);
});

test('a book ttu cannot read is reported as failed', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);

  const reply = await ensure(page, 'broken.epub');

  expect(reply.type).toBe('library.failed');
  expect(reply.message).toContain('Error importing');
});

test('a missing book file is reported as failed', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);

  const reply = await ensure(page, 'gone.epub');

  expect(reply).toMatchObject({ type: 'library.failed' });
  expect(reply.message).toContain('404');
});

test('reading progress comes from ttu’s bookmarks', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'long.epub');
  expect(await progress(page)).toEqual([]);

  await page.goto(`${server.url}/b?id=${book.id}`);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  for (let i = 0; i < 5; i++) {
    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(100);
  }
  // ttu saves its automatic bookmark shortly after a page turn.
  await expect.poll(async () => (await progress(page)).find((b) => b.id === book.id)?.progress ?? 0).toBeGreaterThan(0);
  const [{ progress: fraction, characters }] = await progress(page);
  expect(fraction).toBeLessThan(1);
  // The characters read up to the position, for the reading statistics.
  expect(characters).toBeGreaterThan(0);
});

test('other ttu pages open inside the running page, without a page load', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);
  await page.evaluate(() => (window.__samePage = true));

  await page.evaluate(() => window.hongoLibrary.navigate({ path: '/settings' }));
  await page.waitForURL(/\/settings$/);
  await expect(page.locator('h2', { hasText: 'Theme' })).toBeVisible();
  expect(await page.evaluate(() => window.__samePage)).toBe(true);

  // Only paths on the reader's origin.
  await page.evaluate(() => window.hongoLibrary.navigate({ path: '//example.com/x' }));
  await page.waitForTimeout(300);
  expect(new URL(page.url()).pathname).toBe('/settings');
});

// The app keeps the book list covered until the settings are drawn (book-shown.js).
test('opening the settings reports them drawn once per visit, after the book list is gone', async ({ page }) => {
  await setUp(page);
  await page.addInitScript({ path: path.join(ASSETS, 'book-shown.js') });
  await page.addInitScript(() => {
    const post = window.hongo.postMessage;
    window.hongo.postMessage = (message) => {
      const parsed = JSON.parse(message);
      if (parsed.type === 'pageShown') {
        parsed.settings = !!Array.from(document.querySelectorAll('h2')).find((h) => h.textContent.includes('Theme'));
        parsed.bookList = !!document.querySelector('input[type=file][accept^="application/epub+zip"]');
      }
      post(JSON.stringify(parsed));
    };
  });
  await page.goto(`${server.url}/manage`);
  const shown = () => page.evaluate(() => window.__messages.filter((m) => m.type === 'pageShown'));
  await page.waitForTimeout(300);
  expect(await shown()).toEqual([]);

  await page.evaluate(() => window.hongoLibrary.navigate({ path: '/settings' }));
  const message = { type: 'pageShown', path: '/settings', settings: true, bookList: false };
  await expect.poll(shown).toEqual([message]);
  await page.waitForTimeout(300);
  expect(await shown()).toHaveLength(1);

  await page.goBack();
  await page.waitForURL(/\/manage$/);
  await page.evaluate(() => window.hongoLibrary.navigate({ path: '/settings' }));
  await expect.poll(shown).toEqual([message, message]);
});

test('touches and keys in a book are reported as reading activity, at most every 15 s', async ({ page }) => {
  await setUp(page);
  await page.addInitScript({ path: path.join(ASSETS, 'reading-activity.js') });
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'long.epub');
  const activity = () => page.evaluate(() => window.__messages.filter((m) => m.type === 'reader.activity').length);
  await page.keyboard.press('ArrowDown');
  expect(await activity()).toBe(0); // not on the book page

  await page.goto(`${server.url}/b?id=${book.id}`);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  await page.keyboard.press('ArrowDown');
  await page.mouse.click(200, 300);
  await page.keyboard.press('ArrowDown');
  expect(await activity()).toBe(1);
});

test('every page turn in a book reports the new reading position', async ({ page }) => {
  await setUp(page);
  await page.addInitScript({ path: path.join(ASSETS, 'reading-activity.js') });
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'long.epub');
  await page.goto(`${server.url}/b?id=${book.id}`);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  const positions = () => page.evaluate(() => window.__messages.filter((m) => m.type === 'reader.position'));

  for (let turn = 1; turn <= 3; turn++) {
    await page.keyboard.press('ArrowDown');
    await expect.poll(async () => (await positions()).length).toBeGreaterThanOrEqual(turn);
  }
  const reported = await positions();
  expect(reported.every((m) => m.id === book.id && Number.isInteger(m.characters))).toBe(true);
  expect(reported.at(-1).characters).toBeGreaterThan(0);
  // The same number ttu keeps in its bookmark.
  expect((await progress(page))[0].characters).toBe(reported.at(-1).characters);
});

// ttu counts characters by text node; a paragraph longer than a page used to keep the count
// (and the bookmark) on the same number page after page.
test('the character count moves on every page, also within a paragraph longer than a page', async ({ page }) => {
  await setUp(page);
  await page.addInitScript({ path: path.join(ASSETS, 'reading-activity.js') });
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'one-paragraph.epub');
  await page.goto(`${server.url}/b?id=${book.id}`);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  const positions = () => page.evaluate(() => window.__messages.filter((m) => m.type === 'reader.position').map((m) => m.characters));

  for (let turn = 1; turn <= 4; turn++) {
    await page.keyboard.press('ArrowDown');
    await expect.poll(async () => (await positions()).length).toBeGreaterThanOrEqual(turn);
  }
  const counts = await positions();
  expect(counts[0]).toBeGreaterThan(0);
  for (let i = 1; i < counts.length; i++) expect(counts[i]).toBeGreaterThan(counts[i - 1]);
});

// A book reopened far into a long chapter (its last page, say) used to stay covered for the
// full 6 s: book-shown.js looked for text on screen only among the chapter's first text nodes.
test('a book reopened far into a chapter is reported shown right away', async ({ page }) => {
  await setUp(page);
  await page.addInitScript({ path: path.join(ASSETS, 'book-shown.js') });
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'ruby.epub');
  await page.evaluate((id) => window.hongoLibrary.open({ ttuId: id }), book.id);
  await page.waitForURL(/\/b\?id=/);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  for (let i = 0; i < 25; i++) {
    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(60);
  }
  await expect.poll(async () => (await progress(page))[0]?.progress ?? 0).toBeGreaterThan(0.1);
  await page.goBack();
  await page.waitForURL(/\/manage$/);

  await page.evaluate(() => (window.__messages.length = 0));
  const start = Date.now();
  await page.evaluate((id) => window.hongoLibrary.open({ ttuId: id }), book.id);
  await expect.poll(() => page.evaluate(() => window.__messages.filter((m) => m.type === 'bookShown').length), { timeout: 3_000 }).toBe(1);
  expect(Date.now() - start).toBeLessThan(2_500);
});

test('resetting progress makes ttu open the book at the start', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'long.epub');
  await page.goto(`${server.url}/b?id=${book.id}`);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  for (let i = 0; i < 5; i++) {
    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(100);
  }
  await expect.poll(async () => (await progress(page)).length).toBe(1);
  // The app resets from the library, never while ttu shows the book.
  await page.goto(`${server.url}/manage`);

  expect(await call(page, 'resetProgress', { ttuId: book.id })).toMatchObject({ type: 'library.reset' });
  expect(await progress(page)).toEqual([]);

  await page.goto(`${server.url}/b?id=${book.id}`);
  await expect(page.locator('div.fixed.inset-0.text-7xl')).toHaveCount(0, { timeout: 5_000 });
  await expect(page.locator('.book-content p').first()).toBeInViewport();
});

test('removing a book deletes it from ttu', async ({ page }) => {
  await setUp(page);
  await page.goto(`${server.url}/manage`);
  const book = await ensure(page, 'neko.epub');

  expect(await call(page, 'remove', { ttuId: book.id })).toMatchObject({ type: 'library.removed' });

  const ids = await page.evaluate(
    () =>
      new Promise((resolve) => {
        const request = indexedDB.open('books');
        request.onsuccess = () => {
          const all = request.result.transaction('data').objectStore('data').getAllKeys();
          all.onsuccess = () => resolve(all.result);
        };
      }),
  );
  expect(ids).not.toContain(book.id);
});

test('the bridge never creates ttu’s database itself', async ({ page }) => {
  await setUp(page);
  // Any page on the origin gets the scripts; this one does not load ttu.
  await page.goto(`${server.url}/does-not-exist.txt`);

  expect(await progress(page)).toEqual([]);
  const names = await page.evaluate(async () => (await indexedDB.databases()).map((d) => d.name));
  expect(names).not.toContain('books');
});
