package com.vladgba.keyb
import java.util.*
import kotlin.collections.ArrayList

object JsonParse {
    fun map(jsonString: String): JsonNode {
        val d = parse(jsonString)
        return JsonNode(if (d is Map<*, *>) d else HashMap<String, Any>())
    }

    fun parse(jsonString: String): Any? {
        val stateStack = Stack<State>()
        var currentType: Type
        var expectingColon = false
        val end = jsonString.length - 1
        var i = 0
        var propertyName: String? = null
        var currentContainer: Any?
        var value: Any?
        var current: Char
        try {
            current = jsonString[i]
            while (isWhitespace(current)) current = jsonString[++i]
        } catch (e: StringIndexOutOfBoundsException) {
            throw Exception("Provided JSON string did not contain a value")
        }
        if (current == '{') {
            currentType = Type.OBJECT
            currentContainer = HashMap<String, Any>()
            i++
        } else {
            throw Exception("Unexpected character \"$current\" instead of root value")
        }
        while (i <= end) {
            current = jsonString[i]
            when (currentType) {
                Type.NAME -> {
                    try {
                        val extracted = extractString(jsonString, i)
                        i = extracted.sourceEnd
                        propertyName = extracted.str
                    } catch (e: StringIndexOutOfBoundsException) {
                        throw Exception("String did not have ending quote")
                    }
                    currentType = Type.HEURISTIC
                    expectingColon = true
                    i++
                }
                Type.NUMBER -> {
                    val extracted = extractNumber(jsonString, i)
                    i = extracted.sourceEnd
                    value = extracted.num.toString()

                    if (currentContainer is Map<*, *>) {
                        (currentContainer as MutableMap<String, Any>)[propertyName!!] = value
                        currentType = Type.OBJECT
                    } else if (currentContainer is List<*>) {
                        (currentContainer as MutableList<Any>).add(value)
                        currentType = Type.ARRAY
                    }
                    i++
                }
                Type.STRING -> {
                    try {
                        val extracted = extractString(jsonString, i)
                        i = extracted.sourceEnd
                        value = extracted.str
                    } catch (e: StringIndexOutOfBoundsException) {
                        throw Exception("String did not have ending quote")
                    }

                    if (currentContainer is Map<*, *>) {
                        (currentContainer as MutableMap<String, Any>)[propertyName!!] = value!!
                        currentType = Type.OBJECT
                    } else {
                        (currentContainer as MutableList<Any>).add(value!!)
                        currentType = Type.ARRAY
                    }
                    i++
                }
                Type.HEURISTIC -> {
                    while (isWhitespace(current) && i++ < end) {
                        current = jsonString[i]
                    }
                    if (current != ':' && expectingColon) {
                        stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                        throw Exception("wasn't followed by a colon")
                    }
                    if (current == ',') {
                        i++
                    } else if (current == ':') {
                        if (expectingColon) {
                            expectingColon = false
                            i++
                        } else {
                            stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                            throw Exception("was followed by too many colons")
                        }
                    } else if (isNumber(current)) {
                        currentType = Type.NUMBER
                    } else if (current == '"') {
                        currentType = Type.STRING
                    } else if (current == '{') {
                        stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                        currentType = Type.OBJECT
                        currentContainer = HashMap<Any, Any>()
                        i++
                    } else if (current == '[') {
                        stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                        currentType = Type.ARRAY
                        currentContainer = ArrayList<Any>()
                        i++
                    } else {
                        throw Exception("unexpected character \"" + current + "\" instead of object value")
                    }
                }
                Type.OBJECT -> {
                    while (isWhitespace(current) && i++ < end) current = jsonString[i]
                    when (current) {
                        ',' -> i++
                        '"' -> {
                            currentType = Type.NAME
                        }
                        '}' -> {
                            if (!stateStack.isEmpty()) {
                                val upper = stateStack.pop()
                                val upperContainer = upper.container
                                val parentName = upper.propertyName
                                currentType = upper.type
                                if (upperContainer is Map<*, *>) {
                                    (upperContainer as MutableMap<String, Any>)[parentName!!] = currentContainer!!
                                } else {
                                    (upperContainer as MutableList<Any>).add(currentContainer!!)
                                }
                                currentContainer = upperContainer
                                i++
                            } else {
                                return currentContainer
                            }
                        }
                        else -> {
                            if (!isWhitespace(current)) {
                                throw Exception("unexpected character '" + current + "' (" + current.code.toString() + ") where a property name is expected. Missing quotes?")
                            }
                        }
                    }
                }
                Type.ARRAY -> {
                    while (isWhitespace(current) && i++ < end) current = jsonString[i]
                    if (isNumber(current)) currentType = Type.NUMBER

                    else when (current) {
                        ',' -> i++
                        '"' -> currentType = Type.STRING
                        '{' -> {
                            stateStack.push(State(null, currentContainer, Type.ARRAY))
                            currentType = Type.OBJECT
                            currentContainer = HashMap<Any, Any>()
                            i++
                        }
                        '[' -> {
                            stateStack.push(State(null, currentContainer, Type.ARRAY))
                            currentType = Type.ARRAY
                            currentContainer = ArrayList<Any>()
                            i++
                        }
                        ']' -> {
                            if (!stateStack.isEmpty()) {
                                val upper = stateStack.pop()
                                val upperContainer = upper.container
                                val parentName = upper.propertyName
                                currentType = upper.type
                                if (upperContainer is Map<*, *>) {
                                    (upperContainer as MutableMap<String, Any>)[parentName!!] = currentContainer!!
                                } else {
                                    (upperContainer as MutableList<Any>).add(currentContainer!!)
                                }
                                currentContainer = upperContainer
                                i++
                            } else {
                                return currentContainer
                            }
                        }
                        else -> {
                            stateStack.push(State(propertyName, currentContainer, Type.ARRAY))
                            throw Exception("Unexpected character \"$current\" instead of array value")
                        }
                    }
                }
            }
        }
        throw Exception("Root element wasn't terminated correctly (Missing ']' or '}'?)")
    }

    private fun extractString(jsonString: String, fldStart: Int): ExtractedString {
        var fieldStart = fldStart
        val builder = StringBuilder()
        while (true) {
            val i = indexOfSpecial(jsonString, fieldStart)
            var c = jsonString[i]
            if (c == '"') {
                builder.append(jsonString.substring(fieldStart + 1, i))
                val estr = ExtractedString()
                estr.sourceEnd = i
                estr.str = builder.toString()
                return estr
            } else if (c == '\\') {
                builder.append(jsonString.substring(fieldStart + 1, i))
                c = jsonString[i + 1]
                when (c) {
                    '"' -> builder.append('\"')
                    '\\' -> builder.append('\\')
                    '/' -> builder.append('/')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000c')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> {
                        builder.append(Character.toChars(jsonString.substring(i + 2, i + 6).toInt(16)))
                        fieldStart = i + 5 // Jump over escape sequence and code point
                        continue
                    }
                }
                fieldStart = i + 1 // Jump over escape sequence
            } else {
                throw IndexOutOfBoundsException()
            }
        }
    }

    private fun extractNumber(jsonString: String, fldStart: Int): ExtractedNumber {
        var i = fldStart
        var withDecimal = false
        var withE = false
        val end = jsonString.length - 1
        do {
            val current = jsonString[i]
            if (!withDecimal && current == '.') {
                withDecimal = true
            } else if (!withE && (current == 'e' || current == 'E')) {
                withE = true
            } else if (!isNumber(current) && current != '+') {
                break
            }
        } while (i++ < end)
        val valueString = jsonString.substring(fldStart, i)
        val value = try {
            if (withDecimal || withE) {
                java.lang.Double.valueOf(valueString)
            } else {
                java.lang.Long.valueOf(valueString)
            }
        } catch (e: NumberFormatException) {
            throw Exception("\"" + valueString + "\" expected to be a number, but wasn't")
        }
        return ExtractedNumber().apply {
            sourceEnd = i
            num = value
        }
    }

    private fun indexOfSpecial(str: String, strt: Int): Int {
        var start = strt
        while (++start < str.length && str[start] != '"' && str[start] != '\\');
        return start
    }

    fun isWhitespace(c: Char): Boolean {
        return arrayOf(' ', '\n', '\r', '\t').contains(c)
    }

    fun isNumber(c: Char): Boolean {
        return c >= '0' && c <= '9' || c == '-'
    }
    
    internal class State(val propertyName: String?, val container: Any?, val type: Type)
    enum class Type {
        ARRAY, OBJECT, HEURISTIC, NAME, NUMBER, STRING
    }

    private class ExtractedNumber {
        var sourceEnd = 0
        var num: Number = 0
    }
    private class ExtractedString {
        var sourceEnd = 0
        var str: String? = null
    }

    data class JsonNode(val data: Any?) {
        fun keys(): Set<String>? {
            return if (isAssoc()) ((data as Map<*, *>).keys as Set<String>) else null
        }

        fun isArray(): Boolean {
            return data is ArrayList<*>
        }

        fun isAssoc(): Boolean {
            return data is Map<*, *>
        }

        fun has(k: String): Boolean {
            return if (data is Map<*, *>) data.containsKey(k) else if (data is ArrayList<*>) data.contains(k.toInt()) else false
        }

        fun has(k: Int): Boolean {
            return if (data is Map<*, *>) data.containsKey(k.toString()) else if (data is ArrayList<*>) data.contains(k) else false
        }

        operator fun get(i: Int): JsonNode {
            return JsonNode(if (isArray()) (data as ArrayList<*>)[i] else if (isAssoc()) (data as Map<*, *>)[i.toString()] else data)
        }

        operator fun get(i: String): JsonNode {
            return JsonNode(if (isArray()) (data as ArrayList<*>)[i.toInt()] else (data as Map<*, *>)[i])
        }

        fun str(): String {
            return when (data) {
                is String -> data
                is Int -> data.toString()
                else -> ""
            }
        }

        fun num(): Int {
             return when (data) {
                is String -> try {
                    if (data.length > 0) data.toInt() else 0
                } catch (_: Exception) {
                    0
                }
                is Int -> data
                else -> 0
             }
        }

        fun bool(): Boolean {
            return when (data) {
                is String -> data.trim() == "1"
                is Int -> data == 1
                else -> false
            }
        }

        fun count(): Int {
            return if (isArray()) (data as ArrayList<*>).size else if (isAssoc()) (data as Map<*, *>).size else 0
        }
    }
}