package app.hongo.reader.data.fonts

/** How a downloadable font looks, for its description. */
enum class FontStyle { MINCHO, GOTHIC, ROUNDED, HANDWRITTEN }

/**
 * A Japanese reader font HonGO! offers to download. All are under the SIL Open Font License and
 * cover kana, kanji (JIS levels 1 and 2 and more) and Latin letters. The files come from the
 * Google Fonts repository at a fixed commit and are checked against [sha256].
 */
data class CatalogFont(
    val id: String,
    /** The font's own name, shown on HonGO!'s screen. */
    val name: String,
    /**
     * The CSS font family, as ttu stores it in `fontFamilyGroupOne`: "HonGO " and the name, as
     * ttu declares some of these fonts itself (pointing to files the app doesn't bundle), and
     * a family with an unloadable face never counts as loaded.
     */
    val family: String = "HonGO $name",
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val style: FontStyle,
)

object FontCatalog {
    private const val COMMIT = "47d54ca1ebb14fd864695f1d39dc734f39017c33"
    private const val BASE = "https://raw.githubusercontent.com/google/fonts/$COMMIT/ofl"

    /**
     * Noto Serif JP is left out: it is Android's own "Serif" (Noto Serif CJK), already in the
     * reader's font choice.
     */
    val fonts = listOf(
        CatalogFont(
            id = "biz-ud-mincho",
            name = "BIZ UDMincho",
            fileName = "BIZUDMincho-Regular.ttf",
            url = "$BASE/bizudmincho/BIZUDMincho-Regular.ttf",
            sha256 = "468ee6d9b149ca144809e03841bf18740ecf014e055a00da6ecaf1aaf4165af2",
            sizeBytes = 6_153_932,
            style = FontStyle.MINCHO,
        ),
        CatalogFont(
            id = "shippori-mincho",
            name = "Shippori Mincho",
            fileName = "ShipporiMincho-Regular.ttf",
            url = "$BASE/shipporimincho/ShipporiMincho-Regular.ttf",
            sha256 = "769b5269f0f9bc6534b352c0e6bd856a566e03ff788f107191c2d835863570b2",
            sizeBytes = 8_677_284,
            style = FontStyle.MINCHO,
        ),
        CatalogFont(
            id = "klee-one",
            name = "Klee One",
            fileName = "KleeOne-Regular.ttf",
            url = "$BASE/kleeone/KleeOne-Regular.ttf",
            sha256 = "bf4063f030cc2ae6adf0a11424a1888e5c0eb4438f1f6d02f52294af868e9b3a",
            sizeBytes = 8_724_204,
            style = FontStyle.HANDWRITTEN,
        ),
        CatalogFont(
            id = "biz-ud-gothic",
            name = "BIZ UDGothic",
            fileName = "BIZUDGothic-Regular.ttf",
            url = "$BASE/bizudgothic/BIZUDGothic-Regular.ttf",
            sha256 = "709fcd41e3209fb765da750472f55ccdf925653e9fa7e1eb007cb65c8f749c75",
            sizeBytes = 4_667_376,
            style = FontStyle.GOTHIC,
        ),
        CatalogFont(
            id = "zen-maru-gothic",
            name = "Zen Maru Gothic",
            fileName = "ZenMaruGothic-Regular.ttf",
            url = "$BASE/zenmarugothic/ZenMaruGothic-Regular.ttf",
            sha256 = "a0c0b53543e0993ae2225e629c833f3d51495ad31720694ff112ce4ce11111ef",
            sizeBytes = 3_832_756,
            style = FontStyle.ROUNDED,
        ),
    )

    fun byFamily(family: String?): CatalogFont? = fonts.firstOrNull { it.family == family }

    /** Font families ttu declares itself (its `reservedFontNames`); catalog families must differ. */
    val TTU_FONT_FAMILIES = setOf(
        "KZ UDGothic", "KZ UDMincho", "Genei Koburi Mincho v5", "Klee One", "Klee One SemiBold",
        "Noto Sans JP", "Noto Serif JP", "Shippori Mincho", "Serif", "Sans-Serif",
    )

    fun byFileName(name: String): CatalogFont? = fonts.firstOrNull { it.fileName == name }
}
