package com.expressive.keyboard

import kotlin.math.min

/**
 * Bộ máy gõ tiếng Việt Telex dựa trên kiến trúc đồ thị biến đổi phím của BambooEngine (`bamboo-core`):
 * - Lưu lịch sử các phép biến đổi (`Transformation` liên kết tới node `Appending` gốc qua `target`)
 * - Hỗ trợ gõ tự do (freestyle) theo thứ tự bất kỳ kể cả sau phụ âm cuối (`chuyrene → chuyển`, `buowco → buôc`)
 * - Đặt dấu thanh theo chuẩn kiểu mới (`EstdToneStyle`: `hòa`, `hủy`) và tự dịch dấu động khi gõ/xóa
 * - Bộ kiểm tra chính tả ma trận CVC (`spelling.go`) + Auto-Restore từ nguyên văn ASCII
 * - Gõ đúp hủy dấu cục bộ (`aaa → aa`, `ass → as`, `ww → w`, `noww → now`, `tooss → tôs`)
 * - Backspace xóa từng ký tự hiển thị cuối kèm dịch dấu (`hoàn → hòa → hò → h`) và diff tối thiểu
 */
object Telex {

    /** Thay đổi tối thiểu từ chuỗi đang hiển thị sang chuỗi mới: giữ [keepPrefix], xóa [delete], chèn [insert]. */
    data class Diff(val keepPrefix: Int, val delete: Int, val insert: String)

    private enum class EffectType { APPENDING, MARK_TRANSFORMATION, TONE_TRANSFORMATION }

    private enum class Mark(val code: Int) {
        NONE(0),
        HAT(1),
        BREVE(2),
        HORN(3),
        DASH(4),
        RAW(5)
    }

    private enum class Tone(val code: Int) {
        NONE(0),
        GRAVE(1),
        ACUTE(2),
        HOOK(3),
        TILDE(4),
        DOT(5)
    }

    private data class Rule(
        val key: Char,
        val effect: Int,
        val effectType: EffectType,
        val effectOn: Char,
        val result: Char,
        val appendedRules: List<Rule> = emptyList()
    )

    private class Transformation(
        var rule: Rule,
        var target: Transformation? = null,
        val isUpperCase: Boolean = false,
        val isUndo: Boolean = false
    )

    private const val MODE_VIETNAMESE = 1 shl 0
    private const val MODE_ENGLISH = 1 shl 1
    private const val MODE_TONE_LESS = 1 shl 2
    private const val MODE_MARK_LESS = 1 shl 3
    private const val MODE_LOWER_CASE = 1 shl 4

    // Bảng 12 nguyên âm × 6 thanh điệu (0: ngang, 1: huyền, 2: sắc, 3: hỏi, 4: ngã, 5: nặng)
    private const val VOWELS =
        "aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ"

    private val MARKS_MAP: Map<Char, String> = mapOf(
        'a' to "aâă__",
        'â' to "aâă__",
        'ă' to "aâă__",
        'e' to "eê___",
        'ê' to "eê___",
        'o' to "oô_ơ_",
        'ô' to "oô_ơ_",
        'ơ' to "oô_ơ_",
        'u' to "u__ư_",
        'ư' to "u__ư_",
        'd' to "d___đ",
        'đ' to "d___đ"
    )

    private val TONE_NAMES = mapOf(
        "XoaDauThanh" to Tone.NONE,
        "DauSac" to Tone.ACUTE,
        "DauHuyen" to Tone.GRAVE,
        "DauHoi" to Tone.HOOK,
        "DauNga" to Tone.TILDE,
        "DauNang" to Tone.DOT
    )

    // Dùng quy tắc Telex 2 (không kèm phím `[`/`]` vì bàn phím chữ chỉ gửi chữ cái)
    private val TELEX_DEF: Map<Char, String> = mapOf(
        'z' to "XoaDauThanh",
        's' to "DauSac",
        'f' to "DauHuyen",
        'r' to "DauHoi",
        'x' to "DauNga",
        'j' to "DauNang",
        'a' to "A_Â",
        'e' to "E_Ê",
        'o' to "O_Ô",
        'w' to "UOA_ƯƠĂ__Ư",
        'd' to "D_Đ"
    )

    // Ma trận chính tả tiếng Việt theo Bamboo (`spelling.go`), bổ sung `qu + yu` (`quỵu`)
    private val FIRST_CONSONANT_SEQS = listOf(
        "b d đ g gh m n nh p ph r s t tr v z",
        "c h k kh qu th",
        "ch gi l ng ngh x",
        "đ l",
        "h",
        "qu"
    )

    private val VOWEL_SEQS = listOf(
        "ê i ua uê uy y",
        "a iê oa uyê yê",
        "â ă e o oo ô ơ oe u ư uâ uô ươ",
        "oă",
        "uơ",
        "ai ao au âu ay ây eo êu ia iêu iu oai oao oay oeo oi ôi ơi ưa uây ui ưi uôi ươi ươu ưu uya uyu yêu",
        "ă",
        "i",
        "yu"
    )

    private val LAST_CONSONANT_SEQS = listOf(
        "ch nh",
        "c ng",
        "m n p t",
        "k",
        "c"
    )

    private val CV_MATRIX = listOf(
        intArrayOf(0, 1, 2, 5),
        intArrayOf(0, 1, 2, 3, 4, 5),
        intArrayOf(0, 1, 2, 3, 5),
        intArrayOf(6),
        intArrayOf(7),
        intArrayOf(8)
    )

    private val VC_MATRIX = listOf(
        intArrayOf(0, 2),
        intArrayOf(0, 1, 2),
        intArrayOf(1, 2),
        intArrayOf(1, 2),
        intArrayOf(),
        intArrayOf(),
        intArrayOf(3),
        intArrayOf(4),
        intArrayOf()
    )

    private val STOP_CODAS = setOf("c", "k", "p", "t", "ch")
    private val REQUIRE_CODA_VOWELS = setOf("â", "ă", "iê", "uyê", "yê", "uâ", "uô", "oo")
    private val REG_DSL = Regex("([a-zA-Z]+)_(\\p{L}+)([_\\p{L}]*)")
    private val REG_DSL_APPENDING = Regex("(_?)_(\\p{L}+)")
    private val REG_UO_H_TAIL = Regex("(uơ|ưo)\\p{L}+")
    private val REG_UH_O = Regex("(ưo|ươ)")

    private val RULES: List<Rule>
    private val RULES_BY_KEY: Map<Char, List<Rule>>
    private val EFFECT_KEYS: Set<Char>
    private val SUPER_KEYS: List<Char>

    init {
        val allRules = ArrayList<Rule>()
        val keys = HashSet<Char>()
        val superKeys = ArrayList<Char>()
        for ((k, line) in TELEX_DEF) {
            keys.add(k)
            if ("uo" in line.lowercase()) superKeys.add(k)
            allRules.addAll(parseRules(k, line))
        }
        RULES = allRules
        RULES_BY_KEY = allRules.groupBy { it.key }
        EFFECT_KEYS = keys
        SUPER_KEYS = superKeys
    }

    fun diff(old: String, new: String): Diff {
        val limit = min(old.length, new.length)
        var lcp = 0
        while (lcp < limit && old[lcp] == new[lcp]) lcp++
        while (lcp > 0 && old[lcp - 1].isHighSurrogate()) lcp--
        return Diff(lcp, old.length - lcp, new.substring(lcp))
    }

    /** Chuyển đổi một chuỗi phím thô thành chuỗi hiển thị (dùng cho unit test và gọi một lần). */
    fun compose(raw: String): String {
        val s = Session()
        for (ch in raw) s.input(ch)
        return s.display
    }

    private fun parseRules(key: Char, line: String): List<Rule> {
        val tone = TONE_NAMES[line]
        if (tone != null) {
            return listOf(Rule(key, tone.code, EffectType.TONE_TRANSFORMATION, '\u0000', '\u0000'))
        }
        return parseTonelessRules(key, line)
    }

    private fun parseTonelessRules(key: Char, line: String): List<Rule> {
        val rules = ArrayList<Rule>()
        val m = REG_DSL.find(line)
        if (m != null) {
            val lower = line.lowercase()
            val ml = REG_DSL.find(lower)!!
            val effectOns = ml.groupValues[1]
            val results = ml.groupValues[2]
            val tail = ml.groupValues[3]
            for (i in effectOns.indices) {
                val effOn = effectOns[i]
                val res = results[i]
                val mark = findMarkFromChar(res) ?: continue
                rules.addAll(parseTonelessRule(key, effOn, res, mark))
            }
            getAppendingRule(key, tail)?.let { rules.add(it) }
        } else {
            getAppendingRule(key, line)?.let { rules.add(it) }
        }
        return rules
    }

    private fun parseTonelessRule(key: Char, effectOn: Char, result: Char, mark: Mark): List<Rule> {
        val rules = ArrayList<Rule>()
        for (chr in getMarkFamily(effectOn)) {
            if (chr == result) {
                if (isVowel(chr)) {
                    for (t in 0..5) {
                        rules.add(
                            Rule(
                                key = key,
                                effect = Mark.NONE.code,
                                effectType = EffectType.MARK_TRANSFORMATION,
                                effectOn = addToneToChar(result, t),
                                result = addToneToChar(effectOn, t)
                            )
                        )
                    }
                } else {
                    rules.add(Rule(key, Mark.NONE.code, EffectType.MARK_TRANSFORMATION, result, effectOn))
                }
            } else if (isVowel(chr)) {
                for (t in 0..5) {
                    rules.add(
                        Rule(
                            key = key,
                            effect = mark.code,
                            effectType = EffectType.MARK_TRANSFORMATION,
                            effectOn = addToneToChar(chr, t),
                            result = addToneToChar(result, t)
                        )
                    )
                }
            } else {
                rules.add(Rule(key, mark.code, EffectType.MARK_TRANSFORMATION, chr, result))
            }
        }
        return rules
    }

    private fun getAppendingRule(key: Char, value: String): Rule? {
        val m = REG_DSL_APPENDING.find(value) ?: return null
        val chars = m.groupValues[2]
        if (chars.isEmpty()) return null
        val extra = if (chars.length > 1) {
            chars.drop(1).map { ch ->
                Rule(key, 0, EffectType.APPENDING, ch, ch)
            }
        } else emptyList()
        return Rule(key, 0, EffectType.APPENDING, chars[0], chars[0], extra)
    }

    private fun getMarkFamily(chr: Char): List<Char> {
        val s = MARKS_MAP[chr] ?: return emptyList()
        return s.filter { it != '_' }.toList()
    }

    private fun findMarkPosition(chr: Char): Int {
        val s = MARKS_MAP[chr] ?: return -1
        return s.indexOf(chr)
    }

    private fun findMarkFromChar(chr: Char): Mark? {
        val pos = findMarkPosition(chr)
        return Mark.entries.firstOrNull { it.code == pos }
    }

    private fun addMarkToTonelessChar(chr: Char, mark: Int): Char {
        val s = MARKS_MAP[chr] ?: return chr
        val c = s.getOrNull(mark) ?: return chr
        return if (c != '_') c else chr
    }

    private fun addMarkToChar(chr: Char, mark: Int): Char {
        val tone = findToneFromChar(chr)
        val bare = addToneToChar(chr, 0)
        val marked = addMarkToTonelessChar(bare, mark)
        return addToneToChar(marked, tone.code)
    }

    private fun isVowel(chr: Char): Boolean = VOWELS.indexOf(chr) >= 0

    private fun findToneFromChar(chr: Char): Tone {
        val pos = VOWELS.indexOf(chr)
        if (pos < 0) return Tone.NONE
        return Tone.entries[pos % 6]
    }

    private fun addToneToChar(chr: Char, tone: Int): Char {
        val pos = VOWELS.indexOf(chr)
        if (pos < 0) return chr
        val cur = pos % 6
        return VOWELS[pos + tone - cur]
    }

    private fun isAlpha(c: Char): Boolean = (c in 'a'..'z') || (c in 'A'..'Z')

    private fun isVietnameseRune(lowerKey: Char): Boolean =
        findToneFromChar(lowerKey) != Tone.NONE || lowerKey != addMarkToTonelessChar(lowerKey, 0)

    private fun canProcessKey(lowerKey: Char): Boolean =
        isAlpha(lowerKey) || lowerKey in EFFECT_KEYS || isVietnameseRune(lowerKey)

    private fun newAppendingTrans(key: Char, isUpperCase: Boolean): Transformation =
        Transformation(
            rule = Rule(key, 0, EffectType.APPENDING, key, key),
            target = null,
            isUpperCase = isUpperCase
        )

    private fun generateAppendingTrans(rules: List<Rule>, lowerKey: Char, isUpperCase: Boolean): Transformation {
        for (rule in rules) {
            if (rule.key == lowerKey && rule.effectType == EffectType.APPENDING) {
                val up = isUpperCase || rule.effectOn.isUpperCase()
                val low = rule.effectOn.lowercaseChar()
                return Transformation(
                    rule = rule.copy(effectOn = low, result = low),
                    target = null,
                    isUpperCase = up
                )
            }
        }
        return newAppendingTrans(lowerKey, isUpperCase)
    }

    private fun findRootTarget(target: Transformation): Transformation {
        var cur = target
        while (cur.target != null) cur = cur.target!!
        return cur
    }

    private fun findLastAppendingTrans(composition: List<Transformation>): Transformation? {
        for (i in composition.indices.reversed()) {
            if (composition[i].rule.effectType == EffectType.APPENDING) return composition[i]
        }
        return null
    }

    private fun filterAppending(composition: List<Transformation>): List<Transformation> =
        composition.filter { it.rule.effectType == EffectType.APPENDING }

    private fun flatten(composition: List<Transformation>, mode: Int): String {
        val out = StringBuilder(composition.size)
        val appMap = HashMap<Transformation, MutableList<Transformation>>()
        val appList = ArrayList<Transformation>()
        for (trans in composition) {
            if ((mode and MODE_ENGLISH) != 0) {
                if (trans.rule.key == '\u0000') continue
                appList.add(trans)
            } else if (trans.rule.effectType == EffectType.APPENDING) {
                if (trans.rule.key == '\u0000') continue
                appList.add(trans)
            } else if (trans.target != null) {
                appMap.getOrPut(trans.target!!) { ArrayList() }.add(trans)
            }
        }
        for (app in appList) {
            var chr: Char
            if ((mode and MODE_ENGLISH) != 0) {
                chr = app.rule.key
            } else {
                chr = app.rule.effectOn
                val list = appMap[app]
                if (list != null) {
                    for (t in list) {
                        when (t.rule.effectType) {
                            EffectType.MARK_TRANSFORMATION -> {
                                chr = if (t.rule.effect == Mark.RAW.code) app.rule.key
                                else addMarkToChar(chr, t.rule.effect)
                            }
                            EffectType.TONE_TRANSFORMATION -> {
                                chr = addToneToChar(chr, t.rule.effect)
                            }
                            EffectType.APPENDING -> Unit
                        }
                    }
                }
            }
            if ((mode and MODE_TONE_LESS) != 0) chr = addToneToChar(chr, 0)
            if ((mode and MODE_MARK_LESS) != 0) chr = addMarkToChar(chr, 0)
            chr = if ((mode and MODE_LOWER_CASE) != 0) chr.lowercaseChar()
            else if (app.isUpperCase) chr.uppercaseChar()
            else chr
            out.append(chr)
        }
        return out.toString()
    }

    /** Phím dấu đã được hủy là lệnh sửa, không phải chữ cần đưa trở lại khi khôi phục. */
    private fun restorationKeys(composition: List<Transformation>): String {
        val cancelled = HashMap<Transformation, MutableSet<EffectType>>()
        val kept = ArrayList<Transformation>(composition.size)
        for (i in composition.indices.reversed()) {
            val trans = composition[i]
            val target = trans.target
            if (trans.isUndo && target != null) {
                cancelled.getOrPut(target) { HashSet() }.add(trans.rule.effectType)
            }
            if (target != null && cancelled[target]?.contains(trans.rule.effectType) == true) continue
            kept.add(trans)
        }
        kept.reverse()
        return flatten(kept, MODE_ENGLISH)
    }

    private fun extractCvcAppendingTrans(appending: List<Transformation>): Triple<List<Transformation>, List<Transformation>, List<Transformation>> {
        // Quét từ cuối: phụ âm cuối (không phải nguyên âm) -> nguyên âm -> phụ âm đầu
        var idx = appending.size
        while (idx > 0 && !isVowel(appending[idx - 1].rule.result)) idx--
        val tail = appending.subList(idx, appending.size)
        var vStart = idx
        while (vStart > 0 && isVowel(appending[vStart - 1].rule.result)) vStart--
        var fc = appending.subList(0, vStart).toMutableList()
        var vo = appending.subList(vStart, idx).toMutableList()
        var lc = tail.toMutableList()

        if (lc.isNotEmpty() && vo.isEmpty() && fc.isEmpty()) {
            fc = lc
            vo = ArrayList()
            lc = ArrayList()
        }

        // Tách phụ âm đầu ghép `gi` và `qu` (trừ ngoại lệ `giê` + phụ âm cuối như `giêng`)
        if (fc.size == 1 && vo.isNotEmpty() && (
                (fc[0].rule.result == 'g' && vo[0].rule.result == 'i' && vo.size > 1 &&
                    !(vo[1].rule.result == 'e' && lc.isNotEmpty())) ||
                    (fc[0].rule.result == 'q' && vo[0].rule.result == 'u')
                )
        ) {
            fc.add(vo.removeAt(0))
        }
        return Triple(fc, vo, lc)
    }

    private fun extractCvcTrans(composition: List<Transformation>): Triple<List<Transformation>, List<Transformation>, List<Transformation>> {
        val transMap = HashMap<Transformation, MutableList<Transformation>>()
        val appList = ArrayList<Transformation>()
        for (trans in composition) {
            if (trans.target == null) appList.add(trans)
            else transMap.getOrPut(trans.target!!) { ArrayList() }.add(trans)
        }
        val (fcApp, voApp, lcApp) = extractCvcAppendingTrans(appList)
        val fc = ArrayList<Transformation>(fcApp.size * 2).apply {
            addAll(fcApp)
            for (t in fcApp) transMap[t]?.let(::addAll)
        }
        val vo = ArrayList<Transformation>(voApp.size * 2).apply {
            addAll(voApp)
            for (t in voApp) transMap[t]?.let(::addAll)
        }
        val lc = ArrayList<Transformation>(lcApp.size * 2).apply {
            addAll(lcApp)
            for (t in lcApp) transMap[t]?.let(::addAll)
        }
        return Triple(fc, vo, lc)
    }

    private fun lookup(seq: List<String>, input: String, inputIsFull: Boolean, inputIsComplete: Boolean): List<Int>? {
        val ret = ArrayList<Int>()
        val inputLen = input.length
        for (index in seq.indices) {
            val row = seq[index]
            var start = 0
            for (j in 0..row.length) {
                if (j < row.length && row[j] != ' ') continue
                val candLen = j - start
                if (candLen >= inputLen && (!inputIsFull || candLen == inputLen)) {
                    var match = true
                    for (k in 0 until inputLen) {
                        val ic = input[k]
                        val cc = row[start + k]
                        if (ic != cc && !(!inputIsComplete && addMarkToTonelessChar(cc, 0) == ic)) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        ret.add(index)
                        break
                    }
                }
                start = j + 1
            }
        }
        return if (ret.isEmpty()) null else ret
    }

    private fun isValidCVC(fc: String, vo: String, lc: String, inputIsFullComplete: Boolean): Boolean {
        val fcIndexes = if (fc.isNotEmpty()) {
            lookup(FIRST_CONSONANT_SEQS, fc, inputIsFullComplete || vo.isNotEmpty(), true) ?: return false
        } else null
        val voIndexes = if (vo.isNotEmpty()) {
            lookup(VOWEL_SEQS, vo, inputIsFullComplete || lc.isNotEmpty(), inputIsFullComplete) ?: return false
        } else null
        val lcIndexes = if (lc.isNotEmpty()) {
            lookup(LAST_CONSONANT_SEQS, lc, inputIsFullComplete, true) ?: return false
        } else null
        if (voIndexes == null) return fcIndexes != null
        // Phải tồn tại cùng một nhóm nguyên âm thỏa cả ghép âm đầu (CV) lẫn ghép âm cuối (VC)
        val matchedVo = if (fcIndexes != null) {
            voIndexes.filter { v -> fcIndexes.any { f -> v in CV_MATRIX[f] } }
        } else {
            voIndexes
        }
        if (matchedVo.isEmpty()) return false
        if (lcIndexes == null) return true
        return matchedVo.any { v -> VC_MATRIX[v].any { c -> c in lcIndexes } }
    }

    private fun hasValidTone(composition: List<Transformation>, tone: Tone): Boolean {
        if (tone == Tone.NONE || tone == Tone.ACUTE || tone == Tone.DOT) return true
        val (_, _, lc) = extractCvcTrans(composition)
        if (lc.isEmpty()) return true
        val lastConsonants = flatten(lc, MODE_ENGLISH or MODE_LOWER_CASE)
        return lastConsonants !in STOP_CODAS
    }

    private fun isValid(composition: List<Transformation>, inputIsFullComplete: Boolean): Boolean {
        if (composition.size <= 1) return true
        for (i in composition.indices.reversed()) {
            if (composition[i].rule.effectType == EffectType.TONE_TRANSFORMATION) {
                val lastTone = Tone.entries.getOrElse(composition[i].rule.effect) { Tone.NONE }
                if (!hasValidTone(composition, lastTone)) return false
                break
            }
        }
        val (fc, vo, lc) = extractCvcTrans(composition)
        val mode = MODE_VIETNAMESE or MODE_LOWER_CASE or MODE_TONE_LESS
        return isValidCVC(flatten(fc, mode), flatten(vo, mode), flatten(lc, mode), inputIsFullComplete)
    }

    private fun extractLastWord(composition: List<Transformation>): Pair<List<Transformation>, List<Transformation>> {
        for (i in composition.indices.reversed()) {
            val canvas = flatten(composition.subList(i, composition.size), MODE_VIETNAMESE or MODE_LOWER_CASE or MODE_TONE_LESS or MODE_MARK_LESS)
            if (canvas.isEmpty()) continue
            val c = canvas[0]
            if (!isAlpha(c) && c !in EFFECT_KEYS) {
                if (i == composition.size - 1) return composition to emptyList()
                return composition.subList(0, i + 1) to composition.subList(i + 1, composition.size)
            }
        }
        return emptyList<Transformation>() to composition
    }

    private fun extractLastSyllable(composition: List<Transformation>): Pair<List<Transformation>, List<Transformation>> {
        val (prevWord, last) = extractLastWord(composition)
        var anchor = 0
        for (i in last.indices) {
            if (!isValid(last.subList(anchor, i + 1), false)) {
                anchor = i
            }
        }
        val prev = if (anchor > 0) prevWord + last.subList(0, anchor) else prevWord
        return prev to last.subList(anchor, last.size)
    }

    /** Tìm vị trí đặt thanh điệu theo chuẩn kiểu mới (`EstdToneStyle`: `hòa`, `hủy`). */
    private fun findToneTarget(composition: List<Transformation>): Transformation? {
        if (composition.isEmpty()) return null
        val (_, vo, lc) = extractCvcTrans(composition)
        val vowels = filterAppending(vo)
        if (vowels.isEmpty()) return null
        if (vowels.size == 1) return vowels[0]
        if (vowels.size == 2) {
            var target: Transformation? = null
            for (trans in vo) {
                if (trans.rule.result == 'ơ' || trans.rule.result == 'ê') {
                    target = trans.target ?: trans
                }
            }
            if (target != null) return target
            return if (lc.isNotEmpty()) vowels[1] else vowels[0]
        }
        if (vowels.size == 3) {
            val plain = flatten(vowels, MODE_ENGLISH or MODE_LOWER_CASE or MODE_TONE_LESS or MODE_MARK_LESS)
            return if (plain == "uye") vowels[2] else vowels[1]
        }
        return vowels.last()
    }

    private fun getLastToneTransformation(composition: List<Transformation>): Transformation? {
        for (i in composition.indices.reversed()) {
            val t = composition[i]
            if (t.rule.effectType == EffectType.TONE_TRANSFORMATION && t.target != null) return t
        }
        return null
    }

    private fun isFree(composition: List<Transformation>, trans: Transformation?, effectType: EffectType): Boolean {
        for (t in composition) {
            if (t.target == trans && t.rule.effectType == effectType) return false
        }
        return true
    }

    private fun findMarkTarget(composition: List<Transformation>, rules: List<Rule>): Pair<Transformation, Rule>? {
        val str = flatten(composition, MODE_VIETNAMESE)
        // Duyệt theo thứ tự ngược trong composition nhưng dùng ký tự đang hiển thị của từng node Appending
        val currentCharOf = HashMap<Transformation, Char>()
        val appMap = HashMap<Transformation, MutableList<Transformation>>()
        for (t in composition) {
            if (t.rule.effectType == EffectType.APPENDING && t.rule.key != '\u0000') {
                appMap.getOrPut(t) { ArrayList() }
            } else if (t.target != null) {
                appMap.getOrPut(t.target!!) { ArrayList() }.add(t)
            }
        }
        for (t in composition) {
            if (t.rule.effectType == EffectType.APPENDING && t.rule.key != '\u0000') {
                var ch = t.rule.effectOn
                appMap[t]?.forEach { m ->
                    when (m.rule.effectType) {
                        EffectType.MARK_TRANSFORMATION -> {
                            ch = if (m.rule.effect == Mark.RAW.code) t.rule.key else addMarkToChar(ch, m.rule.effect)
                        }
                        EffectType.TONE_TRANSFORMATION -> ch = addToneToChar(ch, m.rule.effect)
                        EffectType.APPENDING -> Unit
                    }
                }
                currentCharOf[t] = ch
            }
        }
        for (i in composition.indices.reversed()) {
            val trans = composition[i]
            val target = findRootTarget(trans)
            val curChar = if (trans.rule.effectType == EffectType.APPENDING) currentCharOf[trans] else trans.rule.result
            for (rule in rules) {
                if (rule.effectType != EffectType.MARK_TRANSFORMATION || rule.effect <= 0) continue
                if (trans.rule.result == rule.effectOn || (trans.rule.effectType == EffectType.APPENDING && curChar == rule.effectOn)) {
                    val candidate = Transformation(rule, target)
                    val tmp = composition + candidate
                    if (str == flatten(tmp, MODE_VIETNAMESE)) continue
                    if (isValid(tmp, false)) return target to rule
                }
            }
        }
        return null
    }

    private fun findTarget(composition: List<Transformation>, applicableRules: List<Rule>): Pair<Transformation?, Rule>? {
        val str = flatten(composition, MODE_VIETNAMESE)
        for (rule in applicableRules) {
            if (rule.effectType != EffectType.TONE_TRANSFORMATION) continue
            val tone = Tone.entries.getOrElse(rule.effect) { Tone.NONE }
            var target: Transformation? = null
            if (isValid(composition, false) && hasValidTone(composition, tone)) {
                target = findToneTarget(composition)
            }
            if (target == null) return null
            if (str == flatten(composition + Transformation(rule, target), MODE_VIETNAMESE)) continue
            if (tone == Tone.NONE && isFree(composition, target, EffectType.TONE_TRANSFORMATION) &&
                findToneFromChar(target.rule.result) == Tone.NONE
            ) {
                return null
            }
            return target to rule
        }
        return findMarkTarget(composition, applicableRules)
    }

    private fun generateUndoTransformations(composition: List<Transformation>, rules: List<Rule>): List<Transformation> {
        val out = ArrayList<Transformation>()
        val str = flatten(composition, MODE_VIETNAMESE or MODE_TONE_LESS or MODE_LOWER_CASE)
        for (rule in rules) {
            if (rule.effectType == EffectType.TONE_TRANSFORMATION) {
                val tone = Tone.entries.getOrElse(rule.effect) { Tone.NONE }
                val target = if (hasValidTone(composition, tone)) findToneTarget(composition) else null
                if (target != null) {
                    out.add(
                        Transformation(
                            rule = Rule('\u0000', 0, EffectType.TONE_TRANSFORMATION, '\u0000', '\u0000'),
                            target = target,
                            isUndo = true
                        )
                    )
                }
            } else if (rule.effectType == EffectType.MARK_TRANSFORMATION) {
                for (i in composition.indices.reversed()) {
                    val trans = composition[i]
                    if (trans.rule.result == rule.effectOn) {
                        val target = findRootTarget(trans)
                        val undoTrans = Transformation(
                            rule = Rule('\u0000', 0, EffectType.MARK_TRANSFORMATION, '\u0000', '\u0000'),
                            target = target,
                            isUndo = true
                        )
                        if (str == flatten(composition + undoTrans, MODE_VIETNAMESE or MODE_TONE_LESS or MODE_LOWER_CASE)) {
                            continue
                        }
                        out.add(undoTrans)
                    }
                }
            }
        }
        return out
    }

    private fun generateFallbackTransformations(rules: List<Rule>, lowerKey: Char, isUpperCase: Boolean): List<Transformation> {
        val out = ArrayList<Transformation>()
        val trans = generateAppendingTrans(rules, lowerKey, isUpperCase)
        out.add(trans)
        for (appRule in trans.rule.appendedRules) {
            val up = isUpperCase || appRule.effectOn.isUpperCase()
            val low = appRule.effectOn.lowercaseChar()
            out.add(
                Transformation(
                    rule = appRule.copy(key = '\u0000', effectOn = low, result = low),
                    target = null,
                    isUpperCase = up
                )
            )
        }
        return out
    }

    private fun applyUowShortcut(syllable: List<Transformation>): Transformation? {
        val str = flatten(syllable, MODE_TONE_LESS or MODE_LOWER_CASE)
        if (SUPER_KEYS.isNotEmpty() && REG_UO_H_TAIL.containsMatchIn(str)) {
            val rules = RULES_BY_KEY[SUPER_KEYS[0]].orEmpty()
            val found = findTarget(syllable, rules)
            if (found != null && found.first != null) {
                val (target, missingRule) = found
                return Transformation(missingRule.copy(key = '\u0000'), target)
            }
        }
        return null
    }

    private fun refreshLastToneTarget(syllable: MutableList<Transformation>) {
        if (!isValid(syllable, false)) return
        val (_, vo, _) = extractCvcTrans(syllable)
        if (vo.isEmpty()) return
        val lastTone = getLastToneTransformation(syllable) ?: return
        val newTarget = findToneTarget(syllable) ?: return
        val oldTarget = lastTone.target
        if (oldTarget != newTarget) {
            for (t in syllable) {
                if (t.rule.effectType == EffectType.TONE_TRANSFORMATION && t.target == oldTarget) {
                    t.target = newTarget
                }
            }
        }
    }

    /** Trạng thái một từ tiếng Việt theo mô hình Bamboo (`Telex 2`, chuẩn đặt dấu kiểu mới `hòa`, `hủy`). */
    class Session {
        private val rawBuf = StringBuilder()
        private var composition = ArrayList<Transformation>()
        private var bypassed = false
        private var displayBuf = ""

        val raw: String get() = rawBuf.toString()
        val display: String get() = displayBuf
        val isBypassed: Boolean get() = bypassed

        fun reset() {
            rawBuf.setLength(0)
            composition.clear()
            bypassed = false
            displayBuf = ""
        }

        private fun hasVowelTrans(list: List<Transformation>): Boolean =
            list.any { it.rule.effectType == EffectType.APPENDING && isVowel(it.rule.result) }

        /** Tránh gõ lặp phím dấu phụ đã hủy (`aaa -> aaaa`, `ddd -> dddd`, `ww -> www`, `uoww -> uowww`) bị biến đổi lại. */
        private fun isTrailingUndoneMarkKey(word: List<Transformation>, lowerKey: Char): Boolean {
            if (word.isEmpty()) return false
            val isMarkKey = RULES_BY_KEY[lowerKey]?.any { it.effectType != EffectType.TONE_TRANSFORMATION } == true
            if (!isMarkKey) return false
            var sawUndo = false
            for (i in word.indices.reversed()) {
                val root = findRootTarget(word[i])
                if (root.rule.effectType != EffectType.APPENDING || root.rule.key != lowerKey) break
                if (word[i].isUndo && word[i].rule.effectType == EffectType.MARK_TRANSFORMATION) {
                    sawUndo = true
                }
            }
            return sawUndo
        }

        fun input(ch: Char): String {
            rawBuf.append(ch)
            if (bypassed) {
                displayBuf += ch
                return displayBuf
            }
            // Sau khi hủy sạch dấu trong một từ đã sai cấu trúc (cus, w...), phần tiếp theo
            // là chữ nguyên văn. Không trượt sang âm tiết mới rồi đặt dấu lại như wword -> wỏd.
            if (composition.any { it.isUndo } && displayBuf.none { isVietnameseRune(it.lowercaseChar()) } &&
                !isValid(composition, false)
            ) {
                bypassed = true
                displayBuf = restorationKeys(composition) + ch
                return displayBuf
            }
            val lower = ch.lowercaseChar()
            val upper = ch.isUpperCase()
            if (!canProcessKey(lower)) {
                composition.add(newAppendingTrans(lower, upper))
                displayBuf = flatten(composition, MODE_VIETNAMESE)
                return displayBuf
            }

            val (_, wordBefore) = extractLastWord(composition)
            if (isTrailingUndoneMarkKey(wordBefore, lower)) {
                composition.add(newAppendingTrans(lower, upper))
                displayBuf = flatten(composition, MODE_VIETNAMESE)
                return displayBuf
            }

            // Chỉ xử lý trên âm tiết cuối cùng của từ (cho phép trượt anchor như `alô`, `SĐ`)
            val (prev, lastSyllable) = extractLastSyllable(composition)
            val updatedSyllable = lastSyllable.toMutableList()
            updatedSyllable.addAll(generateTransformationsFor(updatedSyllable, lower, upper))
            composition = ArrayList<Transformation>(prev.size + updatedSyllable.size).apply {
                addAll(prev)
                addAll(updatedSyllable)
            }

            // Auto-Restore nếu đã phát sinh dấu tiếng Việt nhưng vần/âm tiết rơi vào trạng thái bất hợp lệ (trừ khi vừa undo chủ ý)
            val (_, curSyllable) = extractLastSyllable(composition)
            val (_, lastWord) = extractLastWord(composition)
            val viet = flatten(lastWord, MODE_VIETNAMESE)
            val eng = flatten(lastWord, MODE_ENGLISH)
            val prefixInLastWord = if (lastWord.size > curSyllable.size) {
                lastWord.subList(0, lastWord.size - curSyllable.size)
            } else {
                emptyList()
            }
            val bareConsonantPrefix = prefixInLastWord.isNotEmpty() &&
                !hasVowelTrans(prefixInLastWord) &&
                hasVowelTrans(curSyllable)
            val vowelHiatusSplit = prefixInLastWord.isNotEmpty() &&
                findLastAppendingTrans(prefixInLastWord)?.let { isVowel(it.rule.result) } == true &&
                filterAppending(curSyllable).firstOrNull()?.let { isVowel(it.rule.result) } == true
            val prefixBroken = flatten(prefixInLastWord, MODE_VIETNAMESE) != flatten(prefixInLastWord, MODE_ENGLISH) ||
                bareConsonantPrefix ||
                vowelHiatusSplit
            val syllableInvalid = !isValid(curSyllable, false)
            if (viet != eng && lastWord.none { it.isUndo } && (prefixBroken || syllableInvalid)) {
                bypassed = true
                displayBuf = restorationKeys(composition)
                return displayBuf
            }

            displayBuf = flatten(composition, MODE_VIETNAMESE)
            return displayBuf
        }

        private fun generateTransformationsFor(
            syllable: List<Transformation>,
            lowerKey: Char,
            isUpperCase: Boolean
        ): List<Transformation> {
            val out = ArrayList<Transformation>()
            val applicableRules = RULES_BY_KEY[lowerKey].orEmpty()

            // Gõ đúp phím mặc định sinh `ư` (`w + w -> w`) hoặc gõ đúp `w` liên tiếp sau dấu râu (`uow + w -> uow`)
            if (syllable.isNotEmpty()) {
                val lastRule = syllable.last().rule
                if (lastRule.effectType == EffectType.APPENDING && lastRule.key == lowerKey && lastRule.key != lastRule.result) {
                    out.add(
                        Transformation(
                            rule = Rule('\u0000', Mark.RAW.code, EffectType.MARK_TRANSFORMATION, '\u0000', '\u0000'),
                            target = syllable.last(),
                            isUndo = true
                        )
                    )
                    return out
                }
                if (lowerKey == 'w' && lastRule.key == 'w' && lastRule.effectType == EffectType.MARK_TRANSFORMATION) {
                    val undo = generateUndoTransformations(syllable, applicableRules)
                    if (undo.isNotEmpty()) {
                        out.addAll(undo)
                        out.add(newAppendingTrans(lowerKey, isUpperCase))
                        return out
                    }
                }
            }

            val found = findTarget(syllable, applicableRules)
            if (found != null && found.first != null) {
                val (target, rule) = found
                out.add(Transformation(rule, target, isUpperCase))
                if (rule.effectType == EffectType.MARK_TRANSFORMATION) {
                    val newComp = syllable + out
                    if (!isValid(newComp, true)) {
                        val virtual = findTarget(newComp, applicableRules)
                        if (virtual != null && virtual.first != null) {
                            out.add(Transformation(virtual.second.copy(key = '\u0000'), virtual.first))
                        }
                    }
                }
            } else {
                // Xử lý `ươ`/`ưo` + `o` -> `uô` (`buowco -> buôc`, `cuoiwo -> cuôi`)
                var handledUhO = false
                if (REG_UH_O.containsMatchIn(flatten(syllable, MODE_VIETNAMESE or MODE_TONE_LESS or MODE_LOWER_CASE))) {
                    val (_, vo, _) = extractCvcTrans(syllable)
                    val vowels = filterAppending(vo)
                    if (vowels.isNotEmpty()) {
                        val removeU = Transformation(
                            rule = Rule('\u0000', Mark.NONE.code, EffectType.MARK_TRANSFORMATION, '\u0000', '\u0000'),
                            target = vowels[0]
                        )
                        val f2 = findTarget(syllable + removeU, applicableRules)
                        if (f2 != null && f2.first != null && f2.first != vowels[0]) {
                            out.add(removeU)
                            out.add(Transformation(f2.second, f2.first, isUpperCase))
                            handledUhO = true
                        }
                    }
                }
                if (!handledUhO) {
                    val undo = generateUndoTransformations(syllable, applicableRules)
                    if (undo.isNotEmpty()) {
                        out.addAll(undo)
                        out.add(newAppendingTrans(lowerKey, isUpperCase))
                    } else {
                        val fallback = generateFallbackTransformations(applicableRules, lowerKey, isUpperCase)
                        out.addAll(fallback)
                        val newComp = syllable + out
                        applyUowShortcut(newComp)?.let { out.add(it) }
                    }
                }
            }

            val updated = (syllable + out).toMutableList()
            refreshLastToneTarget(updated)
            return out
        }

        /** Xóa ký tự hiển thị cuối cùng và tính lại vị trí thanh điệu trên phần còn lại (`hoàn → hòa → hò → h`). */
        fun backspace(): String {
            if (rawBuf.isEmpty() && displayBuf.isEmpty()) return ""
            if (bypassed) {
                if (displayBuf.isNotEmpty()) displayBuf = displayBuf.dropLast(1)
                // Xóa theo chữ đang thấy: không giữ lại phím dấu ẩn để hồi sinh chữ đã xóa.
                rawBuf.setLength(0)
                rawBuf.append(displayBuf)
                if (displayBuf.isEmpty()) reset()
                return displayBuf
            }
            val lastApp = findLastAppendingTrans(composition)
            if (lastApp == null) {
                reset()
                return ""
            }
            if (!canProcessKey(lastApp.rule.key)) {
                composition.removeAt(composition.lastIndex)
            } else {
                val (prev, lastWord) = extractLastWord(composition)
                val kept = ArrayList<Transformation>(lastWord.size)
                for (t in lastWord) {
                    if (t === lastApp || t.target === lastApp) continue
                    kept.add(t)
                }
                refreshLastToneTarget(kept)
                composition = ArrayList<Transformation>(prev.size + kept.size).apply {
                    addAll(prev)
                    addAll(kept)
                }
            }
            if (composition.isEmpty()) {
                reset()
                return ""
            }
            rawBuf.setLength(0)
            rawBuf.append(restorationKeys(composition))
            displayBuf = flatten(composition, MODE_VIETNAMESE)
            return displayBuf
        }

        /** Kiểm tra âm tiết hoàn chỉnh khi ngắt từ (`Space`/`Enter`/dấu câu), tự trả nguyên văn nếu không hợp lệ. */
        fun commit(isEnglishWord: (String) -> Boolean = { false }): String {
            if (bypassed) return finalizeRestoration(displayBuf, isEnglishWord)
            if (composition.isEmpty()) return displayBuf
            val (_, lastWord) = extractLastWord(composition)
            if (isValidCommitWord(lastWord)) return displayBuf

            val lastUndoIdx = lastWord.indexOfLast { it.isUndo }
            if (lastUndoIdx >= 0) {
                val (_, curSyllable) = extractLastSyllable(lastWord)
                val prefixInLastWord = if (lastWord.size > curSyllable.size) {
                    lastWord.subList(0, lastWord.size - curSyllable.size)
                } else emptyList()
                val undoTailEnd = if (
                    lastUndoIdx + 1 < lastWord.size &&
                    lastWord[lastUndoIdx + 1].rule.effectType == EffectType.APPENDING &&
                    lastWord[lastUndoIdx + 1].target == null &&
                    lastWord[lastUndoIdx].rule.effect != Mark.RAW.code
                ) {
                    lastUndoIdx + 1
                } else {
                    lastUndoIdx
                }
                val hasKeysAfterUndo = lastWord.drop(undoTailEnd + 1).any { it.rule.effectType == EffectType.APPENDING }
                val hasBrokenPrefix = prefixInLastWord.isNotEmpty() && !isValidCommitWord(prefixInLastWord)
                // Cho phép double-tap kết thúc từ có chủ đích (`noww -> now`, `hass -> has`, `congoo -> congo`),
                // còn nếu vẫn gõ tiếp từ sai hoặc cụm tiền tố lỗi (`issue`, `error`, `access`, `success`, `coffee`, `buffer`) thì khôi phục nguyên văn
                if (!hasKeysAfterUndo && !hasBrokenPrefix) {
                    return displayBuf
                }
            }

            bypassed = true
            return finalizeRestoration(restorationKeys(composition), isEnglishWord)
        }

        /** Chỉ dùng từ điển để phân biệt chữ lặp thật (issue) với phím hủy dấu (cusstom). */
        private fun finalizeRestoration(restored: String, isEnglishWord: (String) -> Boolean): String {
            val physical = rawBuf.toString()
            displayBuf = if (physical != restored && physical.all { isAlpha(it) } &&
                isEnglishWord(physical.lowercase(java.util.Locale.ROOT)) &&
                !isEnglishWord(restored.lowercase(java.util.Locale.ROOT))
            ) physical else restored
            return displayBuf
        }

        private fun isValidCommitWord(word: List<Transformation>): Boolean {
            if (!isValid(word, true)) return false
            val (_, vo, lc) = extractCvcTrans(word)
            val codaLow = flatten(lc, MODE_ENGLISH or MODE_LOWER_CASE)
            val vStr = flatten(vo, MODE_VIETNAMESE or MODE_LOWER_CASE or MODE_TONE_LESS)
            if (codaLow.isEmpty() && vStr in REQUIRE_CODA_VOWELS) return false
            if (codaLow in STOP_CODAS) {
                val tone = findToneFromChar(flatten(vo, MODE_VIETNAMESE or MODE_LOWER_CASE).firstOrNull { findToneFromChar(it) != Tone.NONE } ?: '\u0000')
                if (tone != Tone.ACUTE && tone != Tone.DOT) return false
            }
            return true
        }
    }
}
