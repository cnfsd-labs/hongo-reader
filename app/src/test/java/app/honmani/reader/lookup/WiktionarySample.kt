package app.honmani.reader.lookup

/**
 * The shape of an entry in the Japanese Wiktionary dictionary (wty-ja-ja, 2026-10-11), "やばい"
 * shortened: word origin, one list of all senses with labels and examples, synonyms, and the
 * links to the web pages.
 */
internal const val WIKTIONARY_YABAI = """[{"type":"structured-content","content":[
    {"tag":"div","content":[
        {"tag":"div","data":{"content":"preamble"},"content":[
            {"tag":"details","data":{"content":"details-entry-Etymology"},"content":[
                {"tag":"summary","data":{"content":"summary-entry"},"content":"語源"},
                {"tag":"div","data":{"content":"Etymology-content"},"content":"形容動詞の やば が形容詞化したもの。"}]}]},
        {"tag":"ol","data":{"content":"glosses"},"lang":"ja","content":[
            {"tag":"li","content":[{"tag":"div","content":[
                {"tag":"div","data":{"content":"tags"},"content":[
                    {"tag":"span","title":"非形式的","data":{"content":"tag","category":"register"},"content":"非形式的"}]},
                "あやうい。あぶない。",
                {"tag":"details","data":{"content":"details-entry-examples"},"content":[
                    {"tag":"summary","data":{"content":"summary-entry"},"content":"1 例"},
                    {"tag":"div","data":{"content":"extra-info"},"content":{"tag":"div","data":{"content":"example-sentence"},"content":[
                        {"tag":"div","data":{"content":"example-sentence-a"},"lang":"ja","content":[
                            "今会うのは非常に",{"tag":"span","data":{"content":"bold-text"},"content":"やばい"},"。"]},
                        {"tag":"div","data":{"content":"example-sentence-c"},"lang":"ja","content":"（出典）"}]}}]}]}]},
            {"tag":"li","content":[{"tag":"div","content":["すばらしい。"]}]}]},
        {"tag":"div","data":{"content":"related-words"},"lang":"ja","content":[
            {"tag":"div","data":{"content":"synonyms"},"content":{"tag":"ul","data":{"content":"synonyms-list"},"content":[
                {"tag":"li","data":{"content":"synonym-item"},"content":"危ない"},
                {"tag":"li","data":{"content":"synonym-item"},"content":"危うい"}]}}]}]},
    {"tag":"div","data":{"content":"backlink"},"content":[
        {"tag":"a","href":"https://ja.wiktionary.org/wiki/やばい#Japanese","content":"Wiktionary"}," | ",
        {"tag":"a","href":"https://kaikki.org/jawiktionary/やばい.html","content":"Kaikki"}]}]}]"""
