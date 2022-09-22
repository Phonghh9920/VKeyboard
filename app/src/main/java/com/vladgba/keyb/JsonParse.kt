package com.vladgba.keyb

import java.util.*

object JsonParse {
    fun map(jsonString: String): Map<String, Any> {
        return parse(jsonString) as Map<String, Any>
    }

    fun list(jsonString: String): List<Any> {
        return parse(jsonString) as List<Any>
    }

    fun string(jsonString: String): String {
        return parse(jsonString) as String
    }

    fun number(jsonString: String): Number? {
        return parse(jsonString) as Number
    }

    fun bool(jsonString: String): Boolean {
        return parse(jsonString) as Boolean
    }

    fun parse(jsonString: String): Any? {
        val stateStack = Stack<State>()
        var currentType: Type
        var expectingComma = false
        var expectingColon = false
        var fieldStart = 0
        val end = jsonString.length - 1
        var i = 0
        var propertyName: String? = null
        var currentContainer: Any? = null
        var value: Any?
        var current: Char
        try {
            while (isWhitespace(jsonString[i].also { current = it })) i++
        } catch (e: Exception) {
            throw Exception("Provided JSON string did not contain a value")
        }
        if (current == '{') {
            currentType = Type.OBJECT
            currentContainer = HashMap<Any, Any>()
            i++
        } else if (current == '[') {
            currentType = Type.ARRAY
            currentContainer = ArrayList<Any>()
            propertyName = null
            i++
        } else if (current == '"') {
            currentType = Type.STRING
            fieldStart = i
        } else if (isLetter(current)) {
            // Assume parsing a constant ("null", "true", "false", etc)
            currentType = Type.CONSTANT
            fieldStart = i
        } else if (isNumberStart(current)) {
            currentType = Type.NUMBER
            fieldStart = i
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
                Type.STRING -> {
                    try {
                        val extracted = extractString(jsonString, i)
                        i = extracted.sourceEnd
                        value = extracted.str
                    } catch (e: StringIndexOutOfBoundsException) {
                        throw Exception("String did not have ending quote")
                    }
                    if (currentContainer == null) {
                        return value
                    } else {
                        expectingComma = true
                        if (currentContainer is Map<*, *>) {
                            (currentContainer as MutableMap<String?, Any?>)[propertyName] = value
                            currentType = Type.OBJECT
                        } else {
                            (currentContainer as MutableList<Any?>).add(value)
                            currentType = Type.ARRAY
                        }
                    }
                    i++
                }
                Type.NUMBER -> {
                    var withDecimal = false
                    var withE = false
                    do {
                        current = jsonString[i]
                        if (!withDecimal && current == '.') {
                            withDecimal = true
                        } else if (!withE && (current == 'e' || current == 'E')) {
                            withE = true
                        } else if (!isNumberStart(current) && current != '+') {
                            break
                        }
                    } while (i++ < end)
                    val valueString = jsonString.substring(fieldStart, i)
                    value = try {
                        if (withDecimal || withE) {
                            java.lang.Double.valueOf(valueString)
                        } else {
                            java.lang.Long.valueOf(valueString)
                        }
                    } catch (e: NumberFormatException) {
                        throw Exception(
                            "\"" + valueString +
                                    "\" expected to be a number, but wasn't"
                        )
                    }
                    if (currentContainer == null) {
                        return value
                    } else {
                        expectingComma = true
                        if (currentContainer is Map<*, *>) {
                            (currentContainer as MutableMap<String?, Any?>)[propertyName] = value
                            currentType = Type.OBJECT
                        } else {
                            (currentContainer as MutableList<Any?>).add(value)
                            currentType = Type.ARRAY
                        }
                    }
                }
                Type.CONSTANT -> {
                    while (isLetter(current) && i++ < end) {
                        current = jsonString[i]
                    }
                    val valueString = jsonString.substring(fieldStart, i)
                    value = when (valueString) {
                        "false" -> false
                        "true" -> true
                        "null" -> null
                        else -> {
                            if (currentContainer is Map<*, *>) {
                                stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                            } else if (currentContainer is List<*>) {
                                stateStack.push(State(propertyName, currentContainer, Type.ARRAY))
                            }
                            throw Exception(
                                "\"" + valueString
                                        + "\" is not a valid constant. Missing quotes?"
                            )
                        }
                    }
                    if (currentContainer == null) {
                        return value
                    } else {
                        expectingComma = true
                        if (currentContainer is Map<*, *>) {
                            (currentContainer as MutableMap<String?, Any?>)[propertyName] = value
                            currentType = Type.OBJECT
                        } else {
                            (currentContainer as MutableList<Any?>).add(value)
                            currentType = Type.ARRAY
                        }
                    }
                }
                Type.HEURISTIC -> {
                    while (isWhitespace(current) && i++ < end) {
                        current = jsonString[i]
                    }
                    if (current != ':' && expectingColon) {
                        stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                        throw Exception("wasn't followed by a colon")
                    }
                    if (current == ':') {
                        if (expectingColon) {
                            expectingColon = false
                            i++
                        } else {
                            stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                            throw Exception("was followed by too many colons")
                        }
                    } else if (current == '"') {
                        currentType = Type.STRING
                        fieldStart = i
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
                    } else if (isLetter(current)) {
                        // Assume parsing a constant ("null", "true", "false", etc)
                        currentType = Type.CONSTANT
                        fieldStart = i
                    } else if (isNumberStart(current)) {
                        // Is a number
                        currentType = Type.NUMBER
                        fieldStart = i
                    } else {
                        throw Exception(
                            "unexpected character \"" + current +
                                    "\" instead of object value"
                        )
                    }
                }
                Type.OBJECT -> {
                    while (isWhitespace(current) && i++ < end) {
                        current = jsonString[i]
                    }
                    if (current == ',') {
                        if (expectingComma) {
                            expectingComma = false
                            i++
                        } else {
                            stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                            throw Exception("followed by too many commas")
                        }
                    } else if (current == '"') {
                        if (expectingComma) {
                            stateStack.push(State(propertyName, currentContainer, Type.OBJECT))
                            throw Exception("wasn't followed by a comma")
                        }
                        currentType = Type.NAME
                        fieldStart = i
                    } else if (current == '}') {
                        if (!stateStack.isEmpty()) {
                            val upper = stateStack.pop()
                            val upperContainer = upper.container
                            val parentName = upper.propertyName
                            currentType = upper.type
                            if (upperContainer is Map<*, *>) {
                                (upperContainer as MutableMap<String?, Any?>)[parentName] = currentContainer
                            } else {
                                (upperContainer as MutableList<Any?>?)!!.add(currentContainer)
                            }
                            currentContainer = upperContainer
                            expectingComma = true
                            i++
                        } else {
                            return currentContainer
                        }
                    } else if (!isWhitespace(current)) {
                        throw Exception(
                            "unexpected character '" + current +
                                    "' where a property name is expected. Missing quotes?"
                        )
                    }
                }
                Type.ARRAY -> {
                    while (isWhitespace(current) && i++ < end) {
                        current = jsonString[i]
                    }
                    if (current != ',' && current != ']' && current != '}' && expectingComma) {
                        stateStack.push(State(null, currentContainer, Type.ARRAY))
                        throw Exception("wasn't preceded by a comma")
                    }
                    if (current == ',') {
                        if (expectingComma) {
                            expectingComma = false
                            i++
                        } else {
                            stateStack.push(State(null, currentContainer, Type.ARRAY))
                            throw Exception("preceded by too many commas")
                        }
                    } else if (current == '"') {
                        currentType = Type.STRING
                        fieldStart = i
                    } else if (current == '{') {
                        stateStack.push(State(null, currentContainer, Type.ARRAY))
                        currentType = Type.OBJECT
                        currentContainer = HashMap<Any, Any>()
                        i++
                    } else if (current == '[') {
                        stateStack.push(State(null, currentContainer, Type.ARRAY))
                        currentType = Type.ARRAY
                        currentContainer = ArrayList<Any>()
                        i++
                    } else if (current == ']') {
                        if (!stateStack.isEmpty()) {
                            val upper = stateStack.pop()
                            val upperContainer = upper.container
                            val parentName = upper.propertyName
                            currentType = upper.type
                            if (upperContainer is Map<*, *>) {
                                (upperContainer as MutableMap<String?, Any?>)[parentName] = currentContainer
                            } else {
                                (upperContainer as MutableList<Any?>?)!!.add(currentContainer)
                            }
                            currentContainer = upperContainer
                            expectingComma = true
                            i++
                        } else {
                            return currentContainer
                        }
                    } else if (isLetter(current)) {
                        // Assume parsing a   ("null", "true", "false", etc)
                        currentType = Type.CONSTANT
                        fieldStart = i
                    } else if (isNumberStart(current)) {
                        // Is a number
                        currentType = Type.NUMBER
                        fieldStart = i
                    } else {
                        stateStack.push(State(propertyName, currentContainer, Type.ARRAY))
                        throw Exception("Unexpected character \"$current\" instead of array value")
                    }
                }
            }
        }
        throw Exception("Root element wasn't terminated correctly (Missing ']' or '}'?)")
    }

    private fun extractString(jsonString: String, fieldStart: Int): ExtractedString {
        var fieldStart = fieldStart
        val builder = StringBuilder()
        while (true) {
            val i = indexOfSpecial(jsonString, fieldStart)
            var c = jsonString[i]
            if (c == '"') {
                builder.append(jsonString.substring(fieldStart + 1, i))
                val `val` = ExtractedString()
                `val`.sourceEnd = i
                `val`.str = builder.toString()
                return `val`
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

    private fun indexOfSpecial(str: String, start: Int): Int {
        var start = start
        while (++start < str.length && str[start] != '"' && str[start] != '\\');
        return start
    }

    fun isWhitespace(c: Char): Boolean {
        return c == ' ' || c == '\n' || c == '\t'
    }

    fun isLetter(c: Char): Boolean {
        return c >= 'a' && c <= 'z'
    }

    fun isNumberStart(c: Char): Boolean {
        return c >= '0' && c <= '9' || c == '-'
    }

    internal class State(val propertyName: String?, val container: Any?, val type: Type)
    enum class Type {
        ARRAY, OBJECT, HEURISTIC, NAME, STRING, NUMBER, CONSTANT
    }

    private class ExtractedString {
        var sourceEnd = 0
        var str: String? = null
    }
}