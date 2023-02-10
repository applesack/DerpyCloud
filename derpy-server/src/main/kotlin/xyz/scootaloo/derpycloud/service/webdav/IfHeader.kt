package xyz.scootaloo.derpycloud.service.webdav

/**
 * @author AppleSack
 * @since 2023/02/05
 */

data class IfHeader(
    val lists: List<IfList>
) {
    companion object {
        val NONE = IfHeader(emptyList())
    }
}

data class IfList(
    var resourceTag: String,
    val conditions: List<Condition>
) {
    companion object {
        val NONE = IfList("", emptyList())
    }
}

data class Condition(
    val not: Boolean,
    val token: String,
    val etag: String
) {
    companion object {
        val NONE = Condition(false, "", "")
    }
}

fun parseIfHeader(s: String): Pair<Boolean, IfHeader> {
    val str = s.trim()
    val (tokenType, _, _) = lex(str)
    return when (tokenType) {
        '('.code -> parseNotTagList(s)
        ANGLE_TOKEN_TYPE -> parseTaggedList(s)
        else -> {
            false to IfHeader.NONE
        }
    }
}

private fun parseNotTagList(s: String): Pair<Boolean, IfHeader> {
    var str = s
    val ifLists = ArrayList<IfList>(4)
    while (true) {
        val r = parseList(str)
        if (!r.success) {
            return false to IfHeader.NONE
        }
        ifLists.add(r.ifList)
        if (r.remaining.isEmpty()) {
            return true to IfHeader(ifLists)
        }
        str = r.remaining
    }
}

private fun parseTaggedList(s: String): Pair<Boolean, IfHeader> {
    var resourceTag = ""
    var n = 0
    var first = true
    var str = s
    val ifLists = ArrayList<IfList>(4)
    while (true) {
        val triple = lex(str)
        when (triple.tokenType) {
            ANGLE_TOKEN_TYPE -> {
                if (!first && n == 0) {
                    return false to IfHeader.NONE
                }
                resourceTag = triple.tokenStr
                n = 0
                str = triple.remaining
            }

            '('.code -> {
                n++
                val r = parseList(str)
                if (!r.success) {
                    return false to IfHeader.NONE
                }
                ifLists.add(r.ifList)
                r.ifList.resourceTag = resourceTag
                if (r.remaining.isEmpty()) {
                    return true to IfHeader(ifLists)
                }
                str = r.remaining
            }

            else -> {
                return false to IfHeader.NONE
            }
        }
        first = false
    }
}

@get:JvmName("ifListSuccess")
private val Triple<Boolean, IfList, String>.success get() = first
private val Triple<Boolean, IfList, String>.ifList get() = second

@get:JvmName("ifListRemaining")
private val Triple<Boolean, IfList, String>.remaining get() = third

private fun parseList(s: String): Triple<Boolean, IfList, String> {
    var triple = lex(s)
    if (triple.tokenType != '('.code) {
        return Triple(false, IfList.NONE, "")
    }

    var str = triple.remaining
    val conditions = ArrayList<Condition>()
    while (true) {
        triple = lex(str)
        if (triple.tokenType == ')'.code) {
            if (conditions.isEmpty()) {
                return Triple(false, IfList.NONE, "")
            }
            return Triple(true, IfList("", conditions), triple.remaining)
        }
        val condTriple = parseConditions(str)
        if (!condTriple.success) {
            return Triple(false, IfList.NONE, "")
        }
        conditions.add(condTriple.condition)
        str = condTriple.remaining
    }
}

private val Triple<Boolean, Condition, String>.success get() = first
private val Triple<Boolean, Condition, String>.condition get() = second

@get:JvmName("condRemaining")
private val Triple<Boolean, Condition, String>.remaining get() = third

private fun parseConditions(s: String): Triple<Boolean, Condition, String> {
    var tripe = lex(s)
    var not = false
    var token = ""
    var etag = ""
    if (tripe.tokenType == NOT_TOKEN_TYPE) {
        not = true
        tripe = lex(tripe.remaining)
    }
    when (tripe.tokenType) {
        STR_TOKEN_TYPE, ANGLE_TOKEN_TYPE -> {
            token = tripe.tokenStr
        }

        SQUARE_TOKEN_TYPE -> {
            etag = tripe.tokenStr
        }

        else -> {
            return Triple(false, Condition.NONE, "")
        }
    }
    return Triple(true, Condition(not, token, etag), tripe.remaining)
}

private const val ERR_TOKEN_TYPE = -1
private const val EOF_TOKEN_TYPE = -2
private const val STR_TOKEN_TYPE = -3
private const val NOT_TOKEN_TYPE = -4
private const val ANGLE_TOKEN_TYPE = -5
private const val SQUARE_TOKEN_TYPE = -6

private val Triple<Int, String, String>.tokenType get() = first
private val Triple<Int, String, String>.tokenStr get() = second
private val Triple<Int, String, String>.remaining get() = third

// first: tokenType, second: tokenStr, second: remaining
private fun lex(s: String): Triple<Int, String, String> {
    val str = s.trimStart(' ', '\t')
    if (str.isEmpty()) {
        return Triple(EOF_TOKEN_TYPE, "", "")
    }

    var i = 0
    while (i < str.length) {
        val match = when (str[i]) {
            '\t', ' ', '(', ')', '<', '>', '[', ']' -> true
            else -> false
        }
        if (match) {
            break
        }
        i++
    }

    val tokenStr: String
    val remaining: String
    if (i != 0) {
        tokenStr = str.substring(0, i)
        remaining = str.substring(i)
        if (tokenStr == "Not") {
            return Triple(NOT_TOKEN_TYPE, "", remaining)
        }
        return Triple(STR_TOKEN_TYPE, tokenStr, remaining)
    }

    val j: Int
    val tokenType: Int
    when (str[0]) {
        '<' -> {
            j = str.indexOf('>')
            tokenType = ANGLE_TOKEN_TYPE
        }

        '[' -> {
            j = str.indexOf(']')
            tokenType = SQUARE_TOKEN_TYPE
        }

        else -> {
            return Triple(str[0].code, "", str.substring(1))
        }
    }
    if (j < 0) {
        return Triple(ERR_TOKEN_TYPE, "", "")
    }
    return Triple(tokenType, str.substring(1, j), str.substring(j + 1))
}
