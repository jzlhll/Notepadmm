package com.allan.atools.richtext.codearea.keywordhelper

import java.util.Locale

/** 常用表情短码离线解析；原始短码仍保存在文档中，显示与导出使用 Unicode 表情。 */
object MarkdownEmojiShortcodes {
    private val symbols = setOf(0x203C, 0x2049, 0x231A, 0x231B, 0x2328, 0x23CF, 0x24C2,
        0x25AA, 0x25AB, 0x25B6, 0x25C0, 0x2B50, 0x2B55, 0x3030, 0x303D, 0x3297, 0x3299)
    private val aliases = mapOf(
        "smile" to "😄", "laughing" to "😆", "satisfied" to "😆", "dizzy_face" to "😵",
        "sob" to "😭", "cold_sweat" to "😰", "sweat_smile" to "😅", "cry" to "😢",
        "triumph" to "😤", "heart_eyes" to "😍", "relieved" to "😌", "+1" to "👍",
        "thumbsup" to "👍", "thumbs_up" to "👍", "-1" to "👎", "thumbsdown" to "👎",
        "thumbs_down" to "👎", "100" to "💯", "clap" to "👏", "bell" to "🔔",
        "gift" to "🎁", "question" to "❓", "bomb" to "💣", "heart" to "❤️",
        "coffee" to "☕", "cyclone" to "🌀", "bow" to "🙇", "kiss" to "💋",
        "pray" to "🙏", "anger" to "💢", "grinning" to "😀", "grin" to "😁",
        "joy" to "😂", "rofl" to "🤣", "smiley" to "😃", "wink" to "😉",
        "blush" to "😊", "innocent" to "😇", "slightly_smiling_face" to "🙂",
        "upside_down_face" to "🙃", "thinking" to "🤔", "thinking_face" to "🤔",
        "sunglasses" to "😎", "kissing_heart" to "😘", "kissing" to "😗",
        "kissing_smiling_eyes" to "😙", "kissing_closed_eyes" to "😚", "yum" to "😋",
        "stuck_out_tongue" to "😛", "stuck_out_tongue_winking_eye" to "😜",
        "stuck_out_tongue_closed_eyes" to "😝", "neutral_face" to "😐",
        "expressionless" to "😑", "no_mouth" to "😶", "smirk" to "😏",
        "unamused" to "😒", "rolling_eyes" to "🙄", "grimacing" to "😬",
        "hushed" to "😯", "open_mouth" to "😮", "astonished" to "😲",
        "sleeping" to "😴", "sleepy" to "😪", "tired_face" to "😫",
        "weary" to "😩", "worried" to "😟", "confused" to "😕",
        "disappointed" to "😞", "pensive" to "😔", "fearful" to "😨",
        "scream" to "😱", "flushed" to "😳", "sweat" to "😓",
        "angry" to "😠", "rage" to "😡", "mask" to "😷", "facepalm" to "🤦",
        "shrug" to "🤷", "poop" to "💩", "shit" to "💩", "hankey" to "💩",
        "ghost" to "👻", "skull" to "💀", "alien" to "👽", "robot" to "🤖",
        "wave" to "👋", "ok_hand" to "👌", "v" to "✌️", "fist" to "✊",
        "punch" to "👊", "raised_hand" to "✋", "raised_hands" to "🙌",
        "muscle" to "💪", "point_up" to "☝️", "point_up_2" to "👆",
        "point_down" to "👇", "point_left" to "👈", "point_right" to "👉",
        "eyes" to "👀", "brain" to "🧠", "broken_heart" to "💔",
        "two_hearts" to "💕", "sparkling_heart" to "💖", "heartpulse" to "💗",
        "blue_heart" to "💙", "green_heart" to "💚", "yellow_heart" to "💛",
        "purple_heart" to "💜", "black_heart" to "🖤", "white_heart" to "🤍",
        "orange_heart" to "🧡", "fire" to "🔥", "sparkles" to "✨",
        "star" to "⭐", "star2" to "🌟", "boom" to "💥", "collision" to "💥",
        "tada" to "🎉", "confetti_ball" to "🎊", "balloon" to "🎈",
        "birthday" to "🎂", "trophy" to "🏆", "medal_sports" to "🏅",
        "rocket" to "🚀", "bulb" to "💡", "warning" to "⚠️", "zap" to "⚡",
        "white_check_mark" to "✅", "heavy_check_mark" to "✔️", "x" to "❌",
        "no_entry" to "⛔", "no_entry_sign" to "🚫", "exclamation" to "❗",
        "heavy_exclamation_mark" to "❗", "grey_question" to "❔",
        "grey_exclamation" to "❕", "interrobang" to "⁉️", "bangbang" to "‼️",
        "arrow_right" to "➡️", "arrow_left" to "⬅️", "arrow_up" to "⬆️",
        "arrow_down" to "⬇️", "recycle" to "♻️", "hourglass" to "⌛",
        "hourglass_flowing_sand" to "⏳", "alarm_clock" to "⏰",
        "sunny" to "☀️", "cloud" to "☁️", "umbrella" to "☔", "snowflake" to "❄️",
        "rainbow" to "🌈", "earth_asia" to "🌏", "earth_americas" to "🌎",
        "earth_africa" to "🌍", "dog" to "🐶", "cat" to "🐱", "panda_face" to "🐼",
        "bear" to "🐻", "monkey_face" to "🐵", "unicorn" to "🦄",
        "cherry_blossom" to "🌸", "rose" to "🌹", "four_leaf_clover" to "🍀",
        "apple" to "🍎", "pizza" to "🍕", "hamburger" to "🍔", "beer" to "🍺",
        "beers" to "🍻", "wine_glass" to "🍷", "soccer" to "⚽",
        "basketball" to "🏀", "computer" to "💻", "keyboard" to "⌨️",
        "iphone" to "📱", "email" to "📧", "memo" to "📝", "pencil" to "📝",
        "pencil2" to "✏️", "book" to "📖", "books" to "📚", "link" to "🔗",
        "lock" to "🔒", "unlock" to "🔓", "key" to "🔑", "hammer" to "🔨",
        "wrench" to "🔧", "gear" to "⚙️", "bug" to "🐛", "construction" to "🚧"
    )

    @JvmStatic
    fun resolve(source: String): String? {
        if (source.length < 3 || source.first() != ':' || source.last() != ':') return null
        return aliases[source.substring(1, source.lastIndex).lowercase(Locale.ROOT)]
    }

    /** 整个表情序列共用字体，避免肤色、旗帜、组合符号被拆成不同字体的片段。 */
    @JvmStatic
    fun unicodeEnd(text: String, start: Int): Int {
        val point = text.codePointAt(start)
        var end = start + Character.charCount(point)
        if (point == '#'.code || point == '*'.code || point in '0'.code..'9'.code) {
            if (text.getOrNull(end) == '\uFE0F') end++
            return if (text.getOrNull(end) == '\u20E3') end + 1 else start
        }
        if (!emojiPoint(point) || text.getOrNull(end) == '\uFE0E') return start
        if (point in 0x1F1E6..0x1F1FF && end < text.length && text.codePointAt(end) in 0x1F1E6..0x1F1FF) end += 2
        while (end < text.length) {
            val next = text.codePointAt(end)
            if (next == 0xFE0F || next in 0x1F3FB..0x1F3FF || next in 0xE0020..0xE007F) {
                end += Character.charCount(next)
            } else if (next == 0x200D && end + 1 < text.length && emojiPoint(text.codePointAt(end + 1))) {
                end += 1 + Character.charCount(text.codePointAt(end + 1))
            } else break
        }
        return end
    }

    private fun emojiPoint(point: Int) = point in 0x1F000..0x1FAFF || point in 0x2600..0x27BF ||
        point in 0x23E9..0x23F3 || point in 0x23F8..0x23FA || point in 0x2B05..0x2B07 ||
        point in 0x2B1B..0x2B1C || point in symbols
}
