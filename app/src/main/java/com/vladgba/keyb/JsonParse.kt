package com.vladgba.keyb

import java.util.*

object JsonParse {
    fun map(jsonString: String): Map<String, Any> {
        return parse(jsonString) as Map<String, Any>
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
                Type.STRING -> {
                    try {
                        val extracted = extractString(jsonString, i)
                        i = extracted.sourceEnd
                        value = extracted.str
                    } catch (e: StringIndexOutOfBoundsException) {
                        throw Exception("String did not have ending quote")
                    }
                    
                    if (currentContainer is Map<*, *>) {
                        (currentContainer as MutableMap<String?, Any?>)[propertyName] = value
                        currentType = Type.OBJECT
                    } else {
                        (currentContainer as MutableList<Any?>).add(value)
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
                        throw Exception(
                            "unexpected character \"" + current +
                                    "\" instead of object value"
                        )
                    }
                }
                Type.OBJECT -> {
                    while (isWhitespace(current) && i++ < end) current = jsonString[i]
                    
                    if (current == ',') {
                        i++
                    } else if (current == '"') {
                        currentType = Type.NAME
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
                            i++
                        } else {
                            return currentContainer
                        }
                    } else if (!isWhitespace(current)) {
                        throw Exception("unexpected character '" + current + "' where a property name is expected. Missing quotes?")
                    }
                }
                Type.ARRAY -> {
                    while (isWhitespace(current) && i++ < end) current = jsonString[i]
                    
                    if (current == ',') {
                        i++
                    } else if (current == '"') {
                        currentType = Type.STRING
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
                            i++
                        } else {
                            return currentContainer
                        }
                    } else {
                        stateStack.push(State(propertyName, currentContainer, Type.ARRAY))
                        throw Exception("Unexpected character \"$current\" instead of array value")
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

    private fun indexOfSpecial(str: String, strt: Int): Int {
        var start = strt
        while (++start < str.length && str[start] != '"' && str[start] != '\\');
        return start
    }

    fun isWhitespace(c: Char): Boolean {
        return c == ' ' || c == '\n' || c == '\t'
    }
    
    internal class State(val propertyName: String?, val container: Any?, val type: Type)
    enum class Type {
        ARRAY, OBJECT, HEURISTIC, NAME, STRING
    }

    private class ExtractedString {
        var sourceEnd = 0
        var str: String? = null
    }
}