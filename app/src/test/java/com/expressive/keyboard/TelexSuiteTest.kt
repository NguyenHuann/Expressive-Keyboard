package com.expressive.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bộ kiểm thử toàn diện 10 nhóm ca biên Telex freestyle (chuẩn dấu kiểu mới `hòa`, `hủy`).
 */
class TelexSuiteTest {

    private fun composeAndCommit(raw: String): String {
        val s = Telex.Session()
        for (ch in raw) s.input(ch)
        return s.commit(TelexEnglishTestWords::contains)
    }

    private fun simulateSentence(stream: String): String {
        val out = StringBuilder()
        val s = Telex.Session()
        var wordLen = 0
        for (ch in stream) {
            if (ch.isLetter()) {
                val res = s.input(ch)
                out.setLength(out.length - wordLen)
                out.append(res)
                wordLen = res.length
            } else {
                val committed = s.commit(TelexEnglishTestWords::contains)
                out.setLength(out.length - wordLen)
                out.append(committed)
                s.reset()
                wordLen = 0
                out.append(ch)
            }
        }
        return out.toString()
    }

    private fun assertAll(group: String, pairs: List<Pair<String, String>>, runner: (String) -> String = { Telex.compose(it) }) {
        val failures = ArrayList<String>()
        for ((input, expected) in pairs) {
            val actual = runner(input)
            if (actual != expected) {
                failures.add("[$group] '$input': expected='$expected', actual='$actual'")
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.joinToString("\n"))
        }
    }

    @Test
    fun group1_tonePermutation() {
        assertAll(
            "Nhóm 1",
            listOf(
                "toans" to "toán",
                "toasn" to "toán",
                "tosan" to "toán",
                "nghiengs" to "nghiéng",
                "nghiensg" to "nghiéng",
                "nghiesng" to "nghiéng",
                "nghiseng" to "nghiéng",
                "hoas" to "hóa",
                "hosa" to "hóa",
                "khuysu" to "khuýu",
                "khuyus" to "khuýu",
                "khusyu" to "khuýu",
                "xas" to "xá",
                "ax" to "ã"
            )
        )
    }

    @Test
    fun group2_lateAndSplitModifiers() {
        assertAll(
            "Nhóm 2",
            listOf(
                "kiepse" to "kiếp",
                "kiespe" to "kiếp",
                "kiesep" to "kiếp",
                "kieesp" to "kiếp",
                "kieeps" to "kiếp",
                "hiepje" to "hiệp",
                "hiejpe" to "hiệp",
                "hieepj" to "hiệp",
                "hieejp" to "hiệp",
                "kiemse" to "kiếm",
                "kiesme" to "kiếm",
                "kieesm" to "kiếm",
                "kieems" to "kiếm",
                "tieuse" to "tiếu",
                "tiesue" to "tiếu",
                "tieeus" to "tiếu",
                "tieesu" to "tiếu",
                "yeuse" to "yếu",
                "yesue" to "yếu",
                "yeeus" to "yếu",
                "muonso" to "muốn",
                "muosno" to "muốn",
                "muoons" to "muốn",
                "muoosn" to "muốn",
                "cuoiso" to "cuối",
                "cuosio" to "cuối",
                "cuoois" to "cuối",
                "cuoosi" to "cuối",
                "tuansa" to "tuấn",
                "tuasna" to "tuấn",
                "daxd" to "đã",
                "dadx" to "đã",
                "ddax" to "đã",
                "duongfwd" to "đường",
                "duongwfd" to "đường",
                "duowngfd" to "đường",
                "dduongfw" to "đường"
            )
        )
    }

    @Test
    fun group3_smartWAndUowOaw() {
        assertAll(
            "Nhóm 3",
            listOf(
                "w" to "ư",
                "ws" to "ứ",
                "wngf" to "ừng",
                "nuocsw" to "nước",
                "nuocws" to "nước",
                "nuowcs" to "nước",
                "nuwocs" to "nước",
                "nuwowcs" to "nước",
                "duocjwd" to "được",
                "duwocjd" to "được",
                "dduowcj" to "được",
                "dduocwj" to "được",
                "nguoiwf" to "người",
                "nguoifw" to "người",
                "nguowif" to "người",
                "nguwoif" to "người",
                "nguwowif" to "người",
                "ruoujw" to "rượu",
                "ruowuj" to "rượu",
                "huow" to "huơ",
                "thuowr" to "thuở",
                "thuowng" to "thương",
                "huowu" to "hươu",
                "hoawcj" to "hoặc",
                "hoacwj" to "hoặc",
                "hoacjw" to "hoặc",
                "xoawn" to "xoăn",
                "xoanw" to "xoăn",
                "quawn" to "quăn",
                "quanw" to "quăn",
                "cuwa" to "cưa",
                "cuaw" to "cưa"
            )
        )
    }

    @Test
    fun group4_giAndQuOnsets() {
        assertAll(
            "Nhóm 4",
            listOf(
                "gif" to "gì",
                "ginf" to "gìn",
                "giuwx" to "giữ",
                "giuxw" to "giữ",
                "gias" to "giá",
                "mias" to "mía",
                "gietse" to "giết",
                "gieets" to "giết",
                "gieset" to "giết",
                "giengse" to "giếng",
                "gieengs" to "giếng",
                "giuongwf" to "giường",
                "giuongfw" to "giường",
                "giuowngf" to "giường",
                "quas" to "quá",
                "ques" to "qué",
                "quys" to "quý",
                "quowf" to "quờ",
                "quocso" to "quốc",
                "quoocs" to "quốc",
                "quosco" to "quốc",
                "quyetse" to "quyết",
                "quyeets" to "quyết",
                "quyeset" to "quyết",
                "quynhf" to "quỳnh",
                "quyuj" to "quỵu"
            )
        )
    }

    @Test
    fun group5_dynamicToneShiftingNewStyle() {
        assertAll(
            "Nhóm 5",
            listOf(
                "hoaf" to "hòa",
                "hoafn" to "hoàn",
                "khoer" to "khỏe",
                "thuyr" to "thủy",
                "tuys" to "túy",
                "tuyst" to "tuýt",
                "toasn" to "toán",
                "khuyur" to "khuỷu",
                "ngoanwf" to "ngoằn",
                "tuyetse" to "tuyết"
            )
        )
    }

    @Test
    fun group6_toneOverrideAndZRemove() {
        assertAll(
            "Nhóm 6 (typing)",
            listOf(
                "asf" to "à",
                "asfrxj" to "ạ",
                "toansf" to "toàn",
                "toasnf" to "toàn",
                "toansz" to "toan",
                "az" to "az"
            )
        )
        assertEquals("kiepf", Telex.compose("kiepf")) // Tương đồng `catr`: chặn dấu huyền trên âm tắc `p`
        assertEquals("kiêp", Telex.compose("kiepsez"))
        assertEquals("kiepsez", composeAndCommit("kiepsez")) // Khôi phục raw_keys khi `kiêp` thiếu sắc/nặng trước `p`
    }

    @Test
    fun group7_doubleTapUndo() {
        assertAll(
            "Nhóm 7",
            listOf(
                "aaa" to "aa",
                "eee" to "ee",
                "ooo" to "oo",
                "ddd" to "dd",
                "aaaa" to "aaa",
                "dddd" to "ddd",
                "ww" to "w",
                "www" to "ww",
                "aww" to "aw",
                "oww" to "ow",
                "uww" to "uw",
                "uoww" to "uow",
                "ass" to "as",
                "aff" to "af",
                "arr" to "ar",
                "axx" to "ax",
                "ajj" to "aj",
                "asss" to "ass",
                "toanss" to "toans",
                "kiepsee" to "kiépe",
                "congoo" to "congo"
            )
        )
    }

    @Test
    fun group8_spellingFilterAndEnglishAutoRestore() {
        assertAll(
            "Nhóm 8 (commit)",
            listOf(
                "window" to "window",
                "word" to "word",
                "free" to "free",
                "java" to "java",
                "class" to "class",
                "print" to "print",
                "string" to "string",
                "block" to "block",
                "add" to "add",
                "odd" to "odd",
                "her" to "hẻ", // Trong Telex thuần (không từ điển Anh), `her` hợp lệ là `hẻ`
                "car" to "cả", // Trong Telex thuần (không từ điển Anh), `car` hợp lệ là `cả`
                "github" to "github",
                "text" to "text",
                "next" to "next",
                "soft" to "soft",
                "left" to "left",
                "fix" to "fix",
                "issue" to "issue",
                "error" to "error",
                "access" to "access",
                "success" to "success",
                "coffee" to "coffee",
                "google" to "google",
                "footer" to "footer",
                "buffer" to "buffer",
                "kiep" to "kiep",
                "hiep" to "hiep",
                "boong" to "bông",
                "xoong" to "xông",
                "soocs" to "sốc",
                "booong" to "boong",
                "xooong" to "xoong",
                "sooocs" to "soóc"
            ),
            ::composeAndCommit
        )
    }

    @Test
    fun group9_invalidModifierRejection() {
        assertAll(
            "Nhóm 9 (commit)",
            listOf(
                "cacao" to "cacao",
                "baoo" to "baoo",
                "toano" to "toano",
                "anha" to "anha",
                "acha" to "acha",
                "tiee" to "tiee"
            ),
            ::composeAndCommit
        )
    }

    @Test
    fun group10_backspaceDelimitersAndCasing() {
        // hoafn<BS> -> hòa (chuẩn mới)
        val s1 = Telex.Session()
        "hoafn".forEach { s1.input(it) }
        assertEquals("hoàn", s1.display)
        assertEquals("hòa", s1.backspace())

        // kiemse<BS>p -> kiếp
        val s2 = Telex.Session()
        "kiemse".forEach { s2.input(it) }
        assertEquals("kiếm", s2.display)
        assertEquals("kiế", s2.backspace())
        assertEquals("kiếp", s2.input('p'))

        // duocjwd -> được -> đượ -> đư -> đ -> ""
        val s3 = Telex.Session()
        "duocjwd".forEach { s3.input(it) }
        assertEquals("được", s3.display)
        assertEquals("đượ", s3.backspace())
        assertEquals("đư", s3.backspace())
        assertEquals("đ", s3.backspace())
        assertEquals("", s3.backspace())

        // Ngắt từ
        assertEquals("toán hòa", simulateSentence("toans hoaf"))
        assertEquals("toán,hòa", simulateSentence("toans,hoaf"))
        assertEquals("(toán)", simulateSentence("(toans)"))

        // Casing
        assertAll(
            "Nhóm 10 (casing)",
            listOf(
                "Kiepse" to "Kiếp",
                "KIEPSE" to "KIẾP",
                "kIepse" to "kIếp",
                "DafD" to "Đà",
                "Ddaf" to "Đà",
                "DDaf" to "Đà",
                "DDAf" to "ĐÀ",
                "Ddaaf" to "Đầ",
                "DDAAf" to "ĐẦ",
                "NUOCWS" to "NƯỚC",
                "Nuocws" to "Nước",
                "UOWCS" to "ƯỚC",
                "Wocs" to "Ước"
            )
        )
    }
}
