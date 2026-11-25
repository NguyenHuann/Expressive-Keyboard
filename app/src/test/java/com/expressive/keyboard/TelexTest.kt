package com.expressive.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra bộ máy Telex dựa trên kiến trúc BambooEngine (`bamboo-core`):
 * 1. Transformation Graph & gõ tự do (freestyle, kể cả sau phụ âm cuối)
 * 2. Đặt dấu chuẩn kiểu mới (`hòa`, `hủy`), tự dịch dấu động & quy tắc `uow`/`buowco`
 * 3. Ma trận chính tả CVC + Auto-Restore khi sai quy tắc & lúc ngắt từ
 * 4. Double-tap Undo cục bộ (`aaa → aa`, `ass → as`, `ww → w`, `noww → now`, `tooss → tôs`)
 * 5. Backspace xóa ký tự hiển thị cuối (`hoàn → hòa → hò → h`) & Diff Rendering
 */
class TelexTest {

    @Test
    fun hornAfterFinalConsonant() {
        assertEquals("trường", Telex.compose("truongwf"))
        assertEquals("nước", Telex.compose("nuocws"))
    }

    @Test
    fun hornAfterFinalConsonantWithoutTone() {
        assertEquals("trương", Telex.compose("truongw"))
        assertEquals("nươc", Telex.compose("nuocw"))
    }

    @Test
    fun quDigraphKeepsPlainU() {
        assertEquals("quớc", Telex.compose("quocws"))
        assertEquals("quặc", Telex.compose("quawcj"))
        assertEquals("quý", Telex.compose("quys"))
        assertEquals("quỳnh", Telex.compose("quynhf"))
        assertEquals("quyền", Telex.compose("quyeenf"))
    }

    @Test
    fun existingRules() {
        assertEquals("ư", Telex.compose("uw"))
        assertEquals("ơ", Telex.compose("ow"))
        assertEquals("ă", Telex.compose("aw"))
        assertEquals("đ", Telex.compose("dd"))
        assertEquals("tiếng", Telex.compose("tieengs"))
        assertEquals("bàn", Telex.compose("banf"))
    }

    @Test
    fun bambooFreestyleAndCrossCodaTransforms() {
        assertEquals("tối", Telex.compose("toiso"))
        assertEquals("tối", Telex.compose("tosio"))
        assertEquals("tối", Telex.compose("toois"))
        assertEquals("tói", Telex.compose("toifs"))
        assertEquals("tối", Telex.compose("tooifs"))
        assertEquals("tối", Telex.compose("tôifs"))
        assertEquals("tòi", Telex.compose("toisf"))
        assertEquals("tồi", Telex.compose("tooisf"))
        assertEquals("tồi", Telex.compose("tốif"))
        assertEquals("toi", Telex.compose("toisz"))
        assertEquals("tôi", Telex.compose("tooisz"))
        assertEquals("tôi", Telex.compose("tốiz"))
        assertEquals("nguyễn", Telex.compose("nguyxene"))
        assertEquals("xoong", Telex.compose("xooong"))
        assertEquals("chuyển", Telex.compose("chuyrene"))
        assertEquals("tiếng", Telex.compose("tiengse"))
        assertEquals("giầm", Telex.compose("giamaf"))
        assertEquals("đài", Telex.compose("ddafi"))
        assertEquals("giá", Telex.compose("gisa"))
        assertEquals("giữa", Telex.compose("giuwax"))
        assertEquals("giường", Telex.compose("giuongwf"))
        assertEquals("chuẩn", Telex.compose("chuaarn"))
        assertEquals("biếc", Telex.compose("bieecs"))
        assertEquals("nhuệ", Telex.compose("nhueej"))
        assertEquals("uế", Telex.compose("uese"))
        assertEquals("alô", Telex.compose("aloo"))
        assertEquals("SĐ", Telex.compose("SDD"))
        assertEquals("VIỆT", Telex.compose("VIEETJ"))
        assertEquals("HIỂU", Telex.compose("HIEEUR"))
        assertEquals("NGƯƠI", Telex.compose("NGUOIW"))
        assertEquals("Hoàn", Telex.compose("Hoafn"))
        assertEquals("Đà", Telex.compose("DDaf"))
    }

    @Test
    fun dynamicToneNewStyleAndSmartW() {
        // Chuẩn kiểu mới: 2 nguyên âm mở -> chữ đầu (hòa, hủy, khỏe); đóng -> chữ sau (hoàn)
        assertEquals("hòa", Telex.compose("hoaf"))
        assertEquals("hòe", Telex.compose("hoef"))
        assertEquals("khỏe", Telex.compose("khoer"))
        assertEquals("hủy", Telex.compose("huyr"))
        assertEquals("thủy", Telex.compose("thuyr"))
        assertEquals("túy", Telex.compose("tuys"))

        // Thêm phụ âm cuối tự dịch dấu; gõ đè thanh điệu
        val s = Telex.Session()
        assertEquals("h", s.input('h'))
        assertEquals("ho", s.input('o'))
        assertEquals("hoa", s.input('a'))
        assertEquals("hòa", s.input('f'))
        assertEquals("hoàn", s.input('n'))
        assertEquals("hòa", Telex.compose("hoasf"))
        assertEquals("hóa", Telex.compose("hoafs"))
        assertEquals("hoan", Telex.compose("hoafnz"))

        // Quy tắc w/uow/ưo của Bamboo
        assertEquals("ươ", Telex.compose("uwow"))
        assertEquals("Thuơ", Telex.compose("Thuow"))
        assertEquals("thuở", Telex.compose("thuowr"))
        assertEquals("dược", Telex.compose("duowcj"))
        assertEquals("được", Telex.compose("dduowcj"))
        assertEquals("được", Telex.compose("dduocwj"))
        assertEquals("dươi", Telex.compose("duwoi"))
        assertEquals("cương", Telex.compose("cuongw"))
        assertEquals("hươu", Telex.compose("huouw"))
        assertEquals("cười", Telex.compose("cuoiwf"))
        assertEquals("tơi", Telex.compose("toowi"))
        assertEquals("buôc", Telex.compose("buowco"))
        assertEquals("buộc", Telex.compose("buowcoj"))
        assertEquals("cuôi", Telex.compose("cuoiwo"))
        assertEquals("cuối", Telex.compose("cuoiwos"))
        assertEquals("gửi", Telex.compose("guirw"))
        assertEquals("mưa", Telex.compose("muaw"))
        assertEquals("chơi", Telex.compose("choiw"))
        assertEquals("ười", Telex.compose("wowfi"))
    }

    @Test
    fun phonotacticValidatorAndAutoRestore() {
        val s = Telex.Session()
        assertEquals("t", s.input('t'))
        assertEquals("te", s.input('e'))
        assertEquals("tẽ", s.input('x'))
        assertEquals("text", s.input('t'))
        assertTrue(s.isBypassed)

        assertEquals("window", Telex.compose("window"))
        assertEquals("word", Telex.compose("word"))
        assertEquals("fast", Telex.compose("fast"))
        assertEquals("just", Telex.compose("just"))
        assertEquals("program", Telex.compose("program"))
        assertEquals("street", Telex.compose("street"))
        assertEquals("press", Telex.compose("press"))
        assertEquals("slow", Telex.compose("slow"))
        assertEquals("draw", Telex.compose("draw"))
        assertEquals("sweet", Telex.compose("sweet"))
        assertEquals("soft", Telex.compose("soft"))
        assertEquals("android", Telex.compose("android"))
        assertEquals("cool", Telex.compose("cool"))
        assertEquals("about", Telex.compose("about"))
        assertEquals("house", Telex.compose("house"))
        assertEquals("banana", Telex.compose("banana"))
        assertEquals("catr", Telex.compose("catr"))
    }

    @Test
    fun commitTimeValidationRestoresEnglishWords() {
        fun commitWord(raw: String): String {
            val s = Telex.Session()
            for (ch in raw) s.input(ch)
            return s.commit(TelexEnglishTestWords::contains)
        }

        assertEquals("saw", commitWord("saw"))
        assertEquals("law", commitWord("law"))
        assertEquals("data", commitWord("data"))
        assertEquals("deep", commitWord("deep"))
        assertEquals("keep", commitWord("keep"))
        assertEquals("meet", commitWord("meet"))
        assertEquals("yes", commitWord("yes"))
        assertEquals("pics", commitWord("pics"))

        // Từ tiếng Việt hợp lệ giữ nguyên khi commit
        assertEquals("hoàn", commitWord("hoafn"))
        assertEquals("nước", commitWord("nuocws"))
        assertEquals("chuyển", commitWord("chuyrene"))
        assertEquals("lịch", commitWord("lichj"))
    }

    @Test
    fun bambooDoubleTapUndo() {
        assertEquals("aa", Telex.compose("aaa"))
        assertEquals("aaa", Telex.compose("aaaa"))
        assertEquals("as", Telex.compose("ass"))
        assertEquals("ass", Telex.compose("asss"))
        assertEquals("has", Telex.compose("hass"))
        assertEquals("w", Telex.compose("ww"))
        assertEquals("uiw", Telex.compose("wiw"))
        assertEquals("now", Telex.compose("noww"))
        assertEquals("linux", Telex.compose("linuxx"))
        assertEquals("tôs", Telex.compose("tooss"))
        assertEquals("tôs", Telex.compose("tosos"))
        assertEquals("tôr", Telex.compose("toorr"))
        assertEquals("nos", Telex.compose("noss"))
        assertEquals("tnoss", Telex.compose("tnoss"))
        assertEquals("dd", Telex.compose("ddd"))
        assertEquals("aw", Telex.compose("aww"))
        assertEquals("ow", Telex.compose("oww"))
        assertEquals("uw", Telex.compose("uww"))
        assertEquals("nw", Telex.compose("nww"))
        assertEquals("duongwj", Telex.compose("duwongwj"))

        // Gõ đúp hủy dấu (noww -> now, hass -> has) phải giữ nguyên khi commit()
        val s = Telex.Session()
        "noww".forEach { s.input(it) }
        assertEquals("now", s.display)
        assertEquals("now", s.commit())
    }

    @Test
    fun backspaceRemovesLastVisibleCharacterAndShiftsTone() {
        // hoàn -> hòa -> hò -> h -> ""
        val s = Telex.Session()
        "hoafn".forEach { s.input(it) }
        assertEquals("hoàn", s.display)
        assertEquals("hòa", s.backspace())
        assertEquals("hò", s.backspace())
        assertEquals("h", s.backspace())
        assertEquals("", s.backspace())
        assertFalse(s.isBypassed)

        // loanj -> lọa, hanhj -> hạn, Thuow -> Thu, reff - BS + f -> rè
        s.reset()
        "loanj".forEach { s.input(it) }
        assertEquals("lọa", s.backspace())

        s.reset()
        "hanhj".forEach { s.input(it) }
        assertEquals("hạn", s.backspace())

        s.reset()
        "Thuow".forEach { s.input(it) }
        assertEquals("Thu", s.backspace())

        s.reset()
        "reff".forEach { s.input(it) }
        s.backspace()
        assertEquals("rè", s.input('f'))

        // turyn - BS - BS -> tủ
        s.reset()
        "turyn".forEach { s.input(it) }
        s.backspace()
        assertEquals("tủ", s.backspace())

        // Xóa hết từ bypass sẽ reset để từ mới gõ tiếng Việt bình thường
        s.reset()
        "text".forEach { s.input(it) }
        assertEquals("text", s.display)
        assertEquals("tex", s.backspace())
        assertEquals("te", s.backspace())
        assertEquals("t", s.backspace())
        assertEquals("", s.backspace())
        assertFalse(s.isBypassed)
        assertEquals("tối", "toiso".fold("") { _, c -> s.input(c) })
    }

    @Test
    fun diffRenderingMinimalEdits() {
        assertEquals(Telex.Diff(keepPrefix = 1, delete = 2, insert = "oàn"), Telex.diff("hòa", "hoàn"))
        assertEquals(Telex.Diff(keepPrefix = 1, delete = 3, insert = "òa"), Telex.diff("hoàn", "hòa"))
        assertEquals(Telex.Diff(keepPrefix = 2, delete = 0, insert = "a"), Telex.diff("hò", "hòa"))
        assertEquals(Telex.Diff(keepPrefix = 2, delete = 1, insert = ""), Telex.diff("hòa", "hò"))
        assertEquals(Telex.Diff(keepPrefix = 1, delete = 1, insert = "ext"), Telex.diff("tẽ", "text"))
    }
}
