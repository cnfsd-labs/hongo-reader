package app.hongo.reader.lookup

import org.junit.Assert.assertEquals
import org.junit.Test

class GlossaryFormatterTest {
    @Test
    fun plainStringsAndTextObjects() {
        assertEquals(
            listOf(Sense(listOf("cat")), Sense(listOf("pussycat"))),
            GlossaryFormatter.format("""["cat",{"type":"text","text":" pussycat "}]"""),
        )
    }

    @Test
    fun jmdictStructuredContent() {
        // As in JMdict for Yomitan (2026): one glossary list per sense.
        val glossary = """[
            {"type":"structured-content","content":{"tag":"ul","data":{"content":"glossary"},
             "lang":"en","content":{"tag":"li","content":"to eat"}}},
            {"type":"structured-content","content":{"tag":"ul","data":{"content":"glossary"},
             "content":[{"tag":"li","content":"to live on (e.g. a salary)"},{"tag":"li","content":"to live off"}]}}
        ]"""

        assertEquals(
            listOf(Sense(listOf("to eat")), Sense(listOf("to live on (e.g. a salary)", "to live off"))),
            GlossaryFormatter.format(glossary),
        )
    }

    @Test
    fun jitendexSensesWithoutTagsAndExamples() {
        // Shape of Jitendex (2026-10): sense groups with part-of-speech tags, numbered senses,
        // and example sentences with furigana that are left out here.
        val glossary = """[{"type":"structured-content","content":[{"tag":"ul","data":{"content":"sense-groups"},
          "content":{"tag":"li","data":{"content":"sense-group"},"content":[
            {"tag":"span","data":{"content":"part-of-speech-info"},"content":"1-dan"},
            {"tag":"ol","content":[
              {"tag":"li","data":{"content":"sense"},"content":[
                {"tag":"ul","data":{"content":"glossary"},"content":{"tag":"li","content":"to eat"}},
                {"tag":"div","data":{"content":"extra-info"},"content":{"tag":"span","content":[
                  "もっと",{"tag":"ruby","content":["果",{"tag":"rt","content":"くだ"}]},"物を食べるべきです。"]}}]},
              {"tag":"li","data":{"content":"sense"},"content":[
                {"tag":"ul","data":{"content":"glossary"},"content":[
                  {"tag":"li","content":"to live on (e.g. a salary)"},{"tag":"li","content":"to subsist on"}]}]}
            ]}]}}]}]"""

        assertEquals(
            listOf(Sense(listOf("to eat")), Sense(listOf("to live on (e.g. a salary)", "to subsist on"))),
            GlossaryFormatter.format(glossary),
        )
    }

    @Test
    fun wiktionarySensesWithoutLabelsExamplesAndLinks() {
        assertEquals(
            listOf(Sense(listOf("あやうい。あぶない。")), Sense(listOf("すばらしい。"))),
            GlossaryFormatter.format(WIKTIONARY_YABAI),
        )
    }

    @Test
    fun unmarkedStructuredContentIsFlattened_withoutFurigana() {
        val glossary = """[{"type":"structured-content","content":[
            {"tag":"div","content":["first line ",{"tag":"ruby","content":["漢",{"tag":"rt","content":"かん"}]}]},
            {"tag":"div","content":"second line"},
            {"tag":"img","path":"a.png"}]}]"""

        assertEquals(listOf(Sense(listOf("first line 漢", "second line"))), GlossaryFormatter.format(glossary))
    }

    @Test
    fun imagesAreSkipped_andInflectedFormEntriesPointToTheirBase() {
        val glossary = """[{"type":"image","path":"img/a.png"},["食べる",["past"]]]"""

        assertEquals(listOf(Sense(listOf("→ 食べる"))), GlossaryFormatter.format(glossary))
    }

    @Test
    fun malformedGlossaryGivesNoSenses() {
        assertEquals(emptyList<Sense>(), GlossaryFormatter.format("not json"))
        assertEquals(emptyList<Sense>(), GlossaryFormatter.format("""{"not":"an array"}"""))
        assertEquals(emptyList<Sense>(), GlossaryFormatter.format("""["", "  "]"""))
    }
}
