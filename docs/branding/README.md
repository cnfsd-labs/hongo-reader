# Branding

The name is 本まに, a pun on the Kansai ほんまに ("really?!") with 本 ("book"). The icon is 本 in Shippori Mincho ExtraBold (SIL Open Font
License 1.1, © The Shippori Mincho Project Authors) on a deep purple gradient.

- `app-icon-artwork.png` (1024×1024) and `play-icon-512.png` (Google Play's 512×512 icon): full
  bleed, the character about 56% of the width.
- The launcher icon (`app/src/main/res/drawable-nodpi/ic_launcher_art.png`, the adaptive icon's
  background layer) has the character at 39% of the layer, inside Android's 66dp safe zone.
- The monochrome launcher icon and the notification icon are vectors of the same character's
  outline (`ic_launcher_monochrome.xml`, `ic_notification.xml`).

Colours: gradient #7457B0 → #4B3C8C → #2F2766 with a #8E6CC4 glow at the top, character
#FBF6EC.

The built-in sample books (`app/src/main/assets/samples/`, built by `scripts/sample-books/`) have
covers in the same type: the title in Shippori Mincho ExtraBold, vertical, on a colour of its own
with a thin double frame and a one-kanji seal (800 × 1200 PNG, `OEBPS/cover.png`); for 吾輩は猫である
indigo #2E3A59 with a gold 猫.
