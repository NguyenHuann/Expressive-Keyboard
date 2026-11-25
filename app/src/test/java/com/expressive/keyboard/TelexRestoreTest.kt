package com.expressive.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal object TelexEnglishTestWords {
    private val words by lazy { File("src/main/assets/dict/en.txt").readLines().map(String::trim).toHashSet() }

    fun contains(word: String): Boolean = word in words
}

class TelexRestoreTest {
    private fun assertCommittedWords(words: List<Pair<String, String>>) {
        val failures = ArrayList<String>()
        for ((raw, expected) in words) {
            val session = Telex.Session()
            raw.forEach { session.input(it) }
            val display = session.display
            val committed = session.commit(TelexEnglishTestWords::contains)
            if (committed != expected) {
                failures += "'$raw': display='$display', commit='$committed', expected='$expected'"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun manualUndoDoesNotReappearOnCommit() {
        assertCommittedWords(listOf(
            "cusstom" to "custom",
            "tesst" to "test",
            "texxt" to "text",
            "hosst" to "host",
            "posst" to "post",
            "porrt" to "port",
            "sofft" to "soft",
            "lefft" to "left",
            "wweb" to "web",
            "wword" to "word",
            "wwifi" to "wifi",
            "wwindow" to "window",
            "rooot" to "root",
            "coool" to "cool",
            "boook" to "book",
            "looop" to "loop",
            "goood" to "good",
            "gooogle" to "google",
            "seee" to "see",
            "treee" to "tree",
            "deeep" to "deep",
            "keeep" to "keep",
            "dddos" to "ddos",
            "maxx" to "max",
            "buss" to "bus",
            "carr" to "car",
            "giff" to "gif",
            "noww" to "now"
        ))
    }

    @Test
    fun literalDoublesStillRestoreEnglishWords() {
        assertCommittedWords(listOf(
            "issue" to "issue",
            "error" to "error",
            "access" to "access",
            "success" to "success",
            "coffee" to "coffee",
            "buffer" to "buffer",
            "class" to "class",
            "add" to "add",
            "odd" to "odd",
            "book" to "book",
            "cool" to "cool",
            "google" to "google"
        ))
    }

    @Test
    fun ambiguousEnglishWordsKeepTheVisibleManualUndo() {
        // Cả of/off và as/ass đều là từ Anh: không thể đoán ý người gõ từ phím vật lý.
        assertCommittedWords(listOf("off" to "of", "ass" to "as", "hass" to "has"))
    }

    @Test
    fun customDoesNotNeedADictionaryToKeepManualUndo() {
        val session = Telex.Session()
        "cusstom".forEach { session.input(it) }
        assertEquals("custom", session.display)
        assertEquals("custom", session.commit())
        assertEquals("custom", session.commit())
    }

    @Test
    fun backspaceCannotReviveCancelledToneKeys() {
        val session = Telex.Session()
        "cusstom".forEach { session.input(it) }
        assertEquals("custo", session.backspace())
        assertEquals("custo", session.commit())

        session.reset()
        "noww".forEach { session.input(it) }
        assertEquals("no", session.backspace())
        session.input('t')
        assertEquals("not", session.commit())

        session.reset()
        "asfrr".forEach { session.input(it) }
        assertEquals("a", session.backspace())
        session.input('b')
        assertEquals("ab", session.commit())
    }

    @Test
    fun deletingAnEscapedLetterDoesNotResurrectItLater() {
        val session = Telex.Session()
        "cusstom".forEach { session.input(it) }
        repeat(4) { session.backspace() }
        assertEquals("cu", session.display)
        "tom".forEach { session.input(it) }
        assertEquals("cutom", session.commit(TelexEnglishTestWords::contains))
    }

    @Test
    fun undoRestorationPreservesCasingAndMultipleEscapes() {
        assertCommittedWords(listOf(
            "Cusstom" to "Custom",
            "CUSSTOM" to "CUSTOM",
            "CoOoL" to "CooL",
            "cusstoms" to "customs",
            "wwords" to "words",
            "asfrr" to "ar"
        ))
    }

    @Test
    fun rawLettersWithoutAnUndoAreNeverDeduplicated() {
        assertCommittedWords(listOf(
            "forrm" to "forrm",
            "sleeep" to "sleeep",
            "form" to "form",
            "sleep" to "sleep",
            "cusstomss" to "customss",
            "bookkeeper" to "bookkeeper",
            "address" to "address"
        ))
    }

    @Test
    fun wordBoundariesApplyOnlyTheRestoredSuffix() {
        fun render(stream: String): String {
            val text = StringBuilder("🙂 ")
            val session = Telex.Session()
            var previous = ""
            fun apply(next: String) {
                val diff = Telex.diff(previous, next)
                text.setLength(text.length - diff.delete)
                text.append(diff.insert)
                previous = next
            }
            for (ch in stream) {
                if (ch.isLetter()) {
                    apply(session.input(ch))
                } else {
                    apply(session.commit(TelexEnglishTestWords::contains))
                    text.append(ch)
                    session.reset()
                    previous = ""
                }
            }
            apply(session.commit(TelexEnglishTestWords::contains))
            return text.toString()
        }

        assertEquals("🙂 custom issue\nweb,root\tcoffee! nước", render("cusstom issue\nwweb,rooot\tcoffee! nuocws"))
    }

    @Test
    fun completeVietnameseWordsAndPartialUndoKeepTheirMarks() {
        assertCommittedWords(listOf(
            "truongwf" to "trường",
            "nuocsw" to "nước",
            "hoafn" to "hoàn",
            "tooss" to "tôs",
            "tosos" to "tôs",
            "toorr" to "tôr",
            "xoong" to "xông",
            "xooong" to "xoong"
        ))
    }

    @Test
    fun englishTieBreakIsIndependentOfTheSystemLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertCommittedWords(listOf("ISSUE" to "ISSUE", "CUSSTOM" to "CUSTOM"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun resetAfterRestorationStillAllowsVietnamese() {
        val session = Telex.Session()
        "cusstom".forEach { session.input(it) }
        assertEquals("custom", session.commit())
        session.reset()
        assertFalse(session.isBypassed)
        "nuocws".forEach { session.input(it) }
        assertEquals("nước", session.commit())
    }
}
