package com.danchi.app.domain

data class MeaningChoiceOption(
    val id: String,
    val wordId: String,
    val meaningId: String,
    val pos: String,
    val posName: String,
    val meaning: String,
    val isCorrect: Boolean
) {
    val displayText: String
        get() = formatMeaningWithPos(pos = pos, posName = posName, meaning = meaning)

    val text: String
        get() = displayText
}

val POS_LABELS = mapOf(
    "n." to "名词",
    "n" to "名词",
    "v." to "动词",
    "v" to "动词",
    "vt." to "及物动词",
    "vt" to "及物动词",
    "vi." to "不及物动词",
    "vi" to "不及物动词",
    "adj." to "形容词",
    "adj" to "形容词",
    "adv." to "副词",
    "adv" to "副词",
    "prep." to "介词",
    "prep" to "介词",
    "conj." to "连词",
    "conj" to "连词",
    "pron." to "代词",
    "pron" to "代词",
    "num." to "数词",
    "num" to "数词",
    "art." to "冠词",
    "art" to "冠词",
    "int." to "感叹词",
    "int" to "感叹词",
    "abbr." to "缩写",
    "abbr" to "缩写",
    "aux." to "助动词",
    "aux" to "助动词",
    "modal." to "情态动词",
    "modal" to "情态动词",
    "phr." to "短语",
    "phr" to "短语",
    "det." to "限定词",
    "det" to "限定词",
    "pl." to "复数",
    "pl" to "复数",
    "sing." to "单数",
    "sing" to "单数"
)

private val PosAliases = buildMap {
    POS_LABELS.keys.forEach { key ->
        put(key.removeSuffix("."), canonicalPos(key))
    }
    put("noun", "n.")
    put("名词", "n.")
    put("verb", "v.")
    put("动词", "v.")
    put("transitiveverb", "vt.")
    put("transitive", "vt.")
    put("及物动词", "vt.")
    put("intransitiveverb", "vi.")
    put("intransitive", "vi.")
    put("不及物动词", "vi.")
    put("adjective", "adj.")
    put("形容词", "adj.")
    put("adverb", "adv.")
    put("副词", "adv.")
    put("preposition", "prep.")
    put("介词", "prep.")
    put("conjunction", "conj.")
    put("连词", "conj.")
    put("pronoun", "pron.")
    put("代词", "pron.")
    put("numeral", "num.")
    put("number", "num.")
    put("数词", "num.")
    put("article", "art.")
    put("冠词", "art.")
    put("interjection", "int.")
    put("感叹词", "int.")
    put("abbreviation", "abbr.")
    put("缩写", "abbr.")
    put("auxiliary", "aux.")
    put("auxiliaryverb", "aux.")
    put("助动词", "aux.")
    put("modalverb", "modal.")
    put("情态动词", "modal.")
    put("phrase", "phr.")
    put("短语", "phr.")
    put("determiner", "det.")
    put("限定词", "det.")
    put("plural", "pl.")
    put("复数", "pl.")
    put("singular", "sing.")
    put("单数", "sing.")
}

private val PosCodeAlternation =
    listOf("modal", "abbr", "prep", "conj", "pron", "sing", "adj", "adv", "aux", "det", "phr", "num", "art", "int", "vt", "vi", "pl", "n", "v")
        .joinToString("|")

private val ChinesePosAlternation =
    listOf("不及物动词", "及物动词", "情态动词", "形容词", "感叹词", "助动词", "限定词", "名词", "动词", "副词", "介词", "连词", "代词", "数词", "冠词", "缩写", "短语", "复数", "单数")
        .joinToString("|")

private val PosPrefixRegex = Regex(
    """^\s*($PosCodeAlternation)(?:\.\s*|[:：]\s*|\s+)(.+)$""",
    RegexOption.IGNORE_CASE
)
private val ChinesePosPrefixRegex = Regex("""^\s*($ChinesePosAlternation)(?:[:：]\s*|\s+)(.+)$""")
private val PosSplitRegex = Regex(
    """(?:\r?\n|\\n)|\s*[;；]\s*(?=(?:$PosCodeAlternation)(?:\.|[:：]|\s))|\s*[;；]\s*(?=$ChinesePosAlternation(?:[:：]|\s))""",
    RegexOption.IGNORE_CASE
)

private fun canonicalPos(pos: String): String {
    val value = pos.trim().lowercase().removeSuffix(".")
    return if (value.isBlank()) "" else "$value."
}

fun normalizePos(pos: String?): String {
    val value = pos
        ?.trim()
        ?.lowercase()
        ?: return ""
    if (value.isBlank()) return ""

    val cleaned = value
        .replace("：", ":")
        .replace("．", ".")
        .replace(Regex("""\s+"""), "")
        .replace(Regex("""。$"""), ".")
        .replace(Regex("""[:：]+$"""), "")

    val aliasKey = cleaned
        .removeSuffix(".")
        .replace(Regex("""[\s_-]+"""), "")

    PosAliases[aliasKey]?.let { return it }
    POS_LABELS[cleaned]?.let { return canonicalPos(cleaned) }
    POS_LABELS[aliasKey]?.let { return canonicalPos(aliasKey) }
    return canonicalPos(cleaned)
}

fun getPosName(pos: String?): String {
    val normalized = normalizePos(pos)
    return POS_LABELS[normalized].orEmpty().ifBlank {
        POS_LABELS[normalized.removeSuffix(".")].orEmpty()
    }
}

fun isKnownPos(pos: String?): Boolean {
    val normalized = normalizePos(pos)
    if (normalized.isBlank()) return false
    return POS_LABELS.containsKey(normalized) || POS_LABELS.containsKey(normalized.removeSuffix("."))
}

fun inferPosFromUnit(unit: String): String {
    return when {
        unit.contains("不及物动词") -> "vi."
        unit.contains("及物动词") -> "vt."
        unit.contains("动词") -> "v."
        unit.contains("形容词") -> "adj."
        unit.contains("副词") -> "adv."
        unit.contains("名词") -> "n."
        unit.contains("介词") -> "prep."
        unit.contains("连词") -> "conj."
        unit.contains("代词") -> "pron."
        unit.contains("数词") -> "num."
        unit.contains("冠词") -> "art."
        unit.contains("感叹词") -> "int."
        unit.contains("限定词") -> "det."
        else -> ""
    }
}

fun parseMeaningText(
    text: String,
    fallbackId: String = "meaning",
    fallbackPos: String = "",
    example: String = "",
    translation: String = ""
): List<WordMeaning> {
    val cleanText = text.trim()
    if (cleanText.isBlank()) return emptyList()

    val fallback = normalizePos(fallbackPos)
    val parts = cleanText
        .split(PosSplitRegex)
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return parts.mapIndexedNotNull { index, part ->
        val match = PosPrefixRegex.find(part)
        val chineseMatch = if (match == null) ChinesePosPrefixRegex.find(part) else null
        val parsedPos = normalizePos(
            match?.groupValues?.getOrNull(1)
                ?: chineseMatch?.groupValues?.getOrNull(1)
        )
        val pos = parsedPos.ifBlank { fallback }
        val meaning = when {
            match != null -> match.groupValues.getOrNull(2)?.trim().orEmpty()
            chineseMatch != null -> chineseMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            else -> part
        }
        meaning.takeIf { it.isNotBlank() }?.let {
            WordMeaning(
                id = "$fallbackId-$index",
                pos = pos,
                posName = getPosName(pos),
                meaning = it,
                example = example,
                translation = translation
            )
        }
    }
}

fun normalizeWordMeanings(
    wordId: String,
    rawMeaning: String,
    rawPos: String = "",
    rawMeanings: List<WordMeaning> = emptyList(),
    example: String = "",
    translation: String = ""
): List<WordMeaning> {
    val fallbackPos = normalizePos(rawPos)
    val explicit = rawMeanings.flatMapIndexed { index, item ->
        val meaning = item.meaning.trim()
        if (meaning.isBlank()) return@flatMapIndexed emptyList()
        val itemPos = normalizePos(item.pos).ifBlank { fallbackPos }
        parseMeaningText(
            text = meaning,
            fallbackId = "$wordId-$index",
            fallbackPos = itemPos,
            example = item.example.ifBlank { example },
            translation = item.translation.ifBlank { translation }
        ).mapIndexed { parsedIndex, parsed ->
            val pos = normalizePos(parsed.pos).ifBlank { itemPos }
            parsed.copy(
                id = if (parsedIndex == 0 && item.id.isNotBlank()) item.id else parsed.id,
                pos = pos,
                posName = item.posName.ifBlank { parsed.posName }.ifBlank { getPosName(pos) },
                example = parsed.example.ifBlank { item.example }.ifBlank { example },
                translation = parsed.translation.ifBlank { item.translation }.ifBlank { translation }
            )
        }
    }

    return explicit.ifEmpty {
        parseMeaningText(
            text = rawMeaning,
            fallbackId = wordId,
            fallbackPos = fallbackPos,
            example = example,
            translation = translation
        )
    }
}

fun normalizeWordMeanings(word: Word): List<WordMeaning> {
    return normalizeWordMeanings(
        wordId = word.id,
        rawMeaning = word.meaning,
        rawPos = word.pos,
        rawMeanings = word.meanings,
        example = word.example,
        translation = word.exampleCn
    )
}

fun formatMeaningWithPos(
    pos: String?,
    posName: String? = "",
    meaning: String? = "",
    showPosName: Boolean = false
): String {
    val normalized = normalizePos(pos)
    val resolvedPosName = posName.orEmpty().ifBlank { getPosName(normalized) }
    val resolvedMeaning = meaning.orEmpty()
    if (normalized.isBlank()) return resolvedMeaning
    return if (showPosName && resolvedPosName.isNotBlank()) {
        "$normalized $resolvedPosName：$resolvedMeaning"
    } else {
        "$normalized $resolvedMeaning"
    }
}

fun formatMeaningWithPos(
    input: WordMeaning,
    showPosName: Boolean = false
): String {
    return formatMeaningWithPos(
        pos = input.pos,
        posName = input.posName,
        meaning = input.meaning,
        showPosName = showPosName
    )
}

val Word.displayMeanings: List<WordMeaning>
    get() = meanings.ifEmpty {
        normalizeWordMeanings(
            wordId = id,
            rawMeaning = meaning,
            rawPos = pos,
            example = example,
            translation = exampleCn
        )
    }

val Word.primaryMeaningText: String
    get() = displayMeanings.firstOrNull()?.meaning ?: meaning

fun meaningChoiceOptionId(wordId: String, meaningId: String): String {
    return "$wordId:$meaningId"
}

fun buildMeaningChoiceOptions(word: Word, candidates: List<Word>): List<MeaningChoiceOption> {
    val distractors = candidates
        .asSequence()
        .filter { it.id != word.id && it.primaryMeaningText.isNotBlank() }
        .distinctBy { it.id }
        .sortedBy { "${word.id}:${it.id}".hashCode() }
        .take(3)
        .map { it.toMeaningChoiceOption(isCorrect = false) }
        .toList()

    return (distractors + word.toMeaningChoiceOption(isCorrect = true))
        .distinctBy { it.wordId }
        .sortedBy { "${word.id}:choice:${it.wordId}".hashCode() }
}

private fun Word.toMeaningChoiceOption(isCorrect: Boolean): MeaningChoiceOption {
    val meaning = displayMeanings.firstOrNull()
    val meaningId = meaning?.id ?: id
    return MeaningChoiceOption(
        id = meaningChoiceOptionId(id, meaningId),
        wordId = id,
        meaningId = meaningId,
        pos = meaning?.pos ?: normalizePos(pos),
        posName = meaning?.posName ?: getPosName(meaning?.pos ?: pos),
        meaning = meaning?.meaning ?: this.meaning,
        isCorrect = isCorrect
    )
}
