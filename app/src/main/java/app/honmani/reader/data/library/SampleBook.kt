package app.honmani.reader.data.library

/**
 * Books that come with the app, in `assets/samples/`: public-domain Aozora Bunko texts with
 * Aozora's credits, as EPUBs with covers made for Honmani (built by `scripts/sample-books/`).
 * Roughly easiest first: children's stories, then short stories, then novels. [FIRST] is the one
 * offered on the first-start screen; Settings offers them all.
 *
 * Works of authors who died before 1950 only, so that they are public domain in Japan, the EU and
 * the US alike (Dazai's, for one, are still protected in the US).
 */
enum class SampleBook(val fileName: String, val title: String, val author: String) {
    TEBUKURO("tebukuro.epub", "手袋を買いに", "新美南吉"),
    GON("gon.epub", "ごんぎつね", "新美南吉"),
    RYORI("ryori.epub", "注文の多い料理店", "宮沢賢治"),
    KUMO("kumo.epub", "蜘蛛の糸", "芥川龍之介"),
    RASHO("rasho.epub", "羅生門", "芥川龍之介"),
    GINGA("ginga.epub", "銀河鉄道の夜", "宮沢賢治"),
    BOCCHAN("bocchan.epub", "坊っちゃん", "夏目漱石"),
    KOKORO("kokoro.epub", "こころ", "夏目漱石"),
    NEKO("neko.epub", "吾輩は猫である（一）", "夏目漱石"),
    ;

    val asset: String get() = "samples/$fileName"

    companion object {
        /** A short children's story: a first book a learner can finish. */
        val FIRST = TEBUKURO
    }
}
