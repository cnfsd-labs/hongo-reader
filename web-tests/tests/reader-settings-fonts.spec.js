// @ts-check
const path = require('node:path');
const { test, expect } = require('@playwright/test');

const SETTINGS = path.join(__dirname, '../../app/src/main/assets/hongo/reader-settings.js');
const FIXTURE = 'file://' + path.join(__dirname, '../fixtures/book.html');

test.beforeEach(async ({ page }) => {
  await page.addInitScript({ path: SETTINGS });
  await page.goto(FIXTURE);
  await page.evaluate(() => window.localStorage.clear());
});

const userFonts = (page) => page.evaluate(() => JSON.parse(window.localStorage.getItem('userfonts') || 'null'));

test('downloaded fonts are listed as ttu user fonts served by the app', async ({ page }) => {
  await page.evaluate(() =>
    window.hongoReaderSettings.fonts({ fonts: [{ name: 'Klee One', fileName: 'KleeOne-Regular.ttf' }] }),
  );

  expect(await userFonts(page)).toEqual([
    { name: 'Klee One', path: '/hongo-fonts/KleeOne-Regular.ttf', fileName: 'KleeOne-Regular.ttf' },
  ]);
});

test("fonts the reader added in ttu are kept; HonGO!'s list is replaced", async ({ page }) => {
  await page.evaluate(() => {
    window.localStorage.setItem(
      'userfonts',
      JSON.stringify([
        { name: 'My Font', path: '/userfonts/my.woff2', fileName: 'my.woff2' },
        { name: 'Klee One', path: '/hongo-fonts/KleeOne-Regular.ttf', fileName: 'KleeOne-Regular.ttf' },
      ]),
    );
    window.hongoReaderSettings.fonts({ fonts: [{ name: 'BIZ UDMincho', fileName: 'BIZUDMincho-Regular.ttf' }] });
  });

  expect(await userFonts(page)).toEqual([
    { name: 'My Font', path: '/userfonts/my.woff2', fileName: 'my.woff2' },
    { name: 'BIZ UDMincho', path: '/hongo-fonts/BIZUDMincho-Regular.ttf', fileName: 'BIZUDMincho-Regular.ttf' },
  ]);
});

test('file names that are not plain font files are refused; a broken list is replaced', async ({ page }) => {
  await page.evaluate(() => {
    window.localStorage.setItem('userfonts', '{not json');
    window.hongoReaderSettings.fonts({
      fonts: [
        { name: 'Bad', fileName: '../books/x.ttf' },
        { name: 'Script', fileName: "a.ttf');}body{display:none" },
        { name: 'Zen Maru Gothic', fileName: 'ZenMaruGothic-Regular.ttf' },
      ],
    });
  });

  expect((await userFonts(page)).map((font) => font.name)).toEqual(['Zen Maru Gothic']);
});

/** Does what ttu's layout does: declares the user fonts in <style id="ttu-userfonts">. */
/** Declares the user fonts as ttu does, once per page (also a page that replaced the one before). */
async function declareUserFonts(page) {
  await page.evaluate(() => {
    if (document.getElementById('ttu-userfonts')) return;
    const fonts = JSON.parse(window.localStorage.getItem('userfonts') || '[]');
    const style = document.createElement('style');
    style.id = 'ttu-userfonts';
    style.textContent = fonts
      .map((font) => `@font-face{font-family: '${font.name}';src: url('${font.path}') format('truetype')}`)
      .join(' ');
    document.head.append(style);
  });
}

const faceStatuses = (page) =>
  page.evaluate(() => Array.from(document.fonts).map((face) => `${face.family.replace(/"/g, '')}: ${face.status}`));

test("ttu's face of the chosen HonGO! font starts loading once declared, so ttu needn't wait for its timeout", async ({
  page,
}) => {
  await page.evaluate(() => {
    window.hongoReaderSettings.fonts({ fonts: [{ name: 'Klee One', fileName: 'KleeOne-Regular.ttf' }] });
    window.localStorage.setItem('fontFamilyGroupOne', 'Klee One');
  });
  await page.reload();

  // One face, ttu's own, requested at once (the file isn't served here, so it ends in an error).
  await expect
    .poll(async () => {
      await declareUserFonts(page);
      return faceStatuses(page);
    }).toEqual([expect.stringMatching(/^Klee One: (loading|loaded|error)$/)]);
});

test('fonts not downloaded by HonGO! are left to ttu', async ({ page }) => {
  await page.evaluate(() => {
    window.localStorage.setItem('userfonts', JSON.stringify([{ name: 'Mine', path: '/userfonts/m.ttf', fileName: 'm.ttf' }]));
    window.localStorage.setItem('fontFamilyGroupOne', 'Mine');
  });
  await page.reload();
  // Declared until the face is listed: right after a reload, the old page can still take it.
  await expect
    .poll(async () => {
      await declareUserFonts(page);
      return faceStatuses(page);
    })
    .toEqual(['Mine: unloaded']);
  await page.waitForTimeout(200);

  expect(await faceStatuses(page)).toEqual(['Mine: unloaded']);
});
