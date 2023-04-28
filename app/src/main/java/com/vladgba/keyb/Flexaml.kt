package com.vladgba.keyb

import android.util.Log

val BEGIN_TOKENS = listOf('(', '[', '{', '<')
val END_TOKENS = listOf(')', ']', '}', '>')
val ASSIGN_TOKENS = listOf(':', '=')
val SEPARATOR_TOKENS = listOf(',', ';')

val escapeMap = mapOf(
    '\\' to "\\\\",
    '\"' to "\\\"",
    '\n' to "\\n",
    '\r' to "\\r",
    '\t' to "\\t",
    '\b' to "\\b",
    '\u0000' to "\\0",
    '\u000C' to "\\f"
)

open class Flexaml(val input: String) {
    private val operatorChars = (BEGIN_TOKENS + END_TOKENS + ASSIGN_TOKENS + SEPARATOR_TOKENS).joinToString("")

    interface ITokenType {
        val name: String
    }

    enum class TokenType : ITokenType {
        WORD, COLON, // COMMA,
        STR, NUM, BEGIN, END, HEX,
    }

    private var pos = 0
    private val length = input.length
    private var row = 1
    private var col = 1

    private var tokenPos = 0
    private val tokens: MutableList<Token> = mutableListOf()
    private val buffer: StringBuilder = StringBuilder()
    private var result = FxmlNode()

    private var tokenCount = 0
    private val warnings = mutableListOf<ParseWarning>()

    open fun parse(): FxmlNode {
        if (tokens.size == 0) tokenize()
        if (tokens.size == 0) return FxmlNode()
        enumerate(result)
        return if (tokens[0].type == TokenType.BEGIN) result[0] else result
    }

    private fun enumerate(v: FxmlNode) {
        while (tokenPos < tokenCount) {
            if (detectedEnd()) break
            if (isValue(tokenPos)) {
                if (tokens.size <= tokenPos + 1) {
                    appendText(v)
                    return warn("Expected at least one more token. We are in root without brackets?")
                }
                if (tokens[tokenPos + 1].type == TokenType.COLON) assign(v)
                else appendText(v)
            } else if (tokens[tokenPos].type == TokenType.BEGIN) {
                if (tokens.size <= tokenPos + 1) return warn("Unexpected open tag")
                appendObj(v)
            } else {
                tokens[tokenPos].apply { warn("Unexpected token " + type + if (text.isEmpty()) "" else " with text `$text`") }
                tokenPos++ // ??? symbol
            }
        }
    }

    private fun detectedEnd() = tokens[tokenPos].type == TokenType.END

    private fun appendText(v: FxmlNode) {
        v.childs.add(tokens[tokenPos++].text)
    }

    private fun appendObj(v: FxmlNode) {
        val newNode = FxmlNode()
        v.childs.add(newNode)
        tokenPos++ // "("
        enumerate(newNode)
        tokenPos++ // ")"
    }

    private fun assign(v: FxmlNode) {
        if (tokens.size <= tokenPos + 2) return warn("Unexpected assignment")
        if (isValue(tokenPos + 2)) {
            v.params[tokens[tokenPos].text] = tokens[tokenPos + 2].text
            tokenPos += 3 // "key" ":" "value"
        } else if (tokens[tokenPos + 2].type == TokenType.BEGIN) {
            val newNode = FxmlNode()
            v.params[tokens[tokenPos].text] = newNode
            tokenPos += 3 // "key" ":" "("
            enumerate(newNode)
            tokenPos++ // ")"
        }
    }

    private fun isValue(i: Int) = tokens[i].type in setOf(TokenType.STR, TokenType.WORD, TokenType.NUM, TokenType.HEX)

    fun tokenize(): List<Token?> {
        while (pos < length) {
            val current: Char = peek(0)
            if (Character.isDigit(current) || current == '-') tokenizeNumber()
            else if (isIdentifierStart(current)) tokenizeWord()
            else if (current == '"') tokenizeText()
            else if (current == '#') tokenizeComment() // TODO: resave = lose comments
            else if (operatorChars.indexOf(current) != -1) tokenizeOperator()
            else next() // whitespaces
        }

        tokenCount = tokens.size
        return tokens
    }

    private fun tokenizeComment() {
        next()
        var current = peek(0)
        while ("\r\n\u0000".indexOf(current) == -1) current = next()
    }

    private fun isIdentifierStart(current: Char): Boolean {
        return Character.isLetter(current) || current == '_' || current == '!'
    }

    private fun isIdentifierPart(current: Char): Boolean {
        return Character.isLetter(current) || current == '_' || Character.isDigit(current)
    }

    private fun tokenizeWord() {
        clearBuffer()
        buffer.append(peek(0))
        var current = next()
        while (true) {
            if (!isIdentifierPart(current)) break
            buffer.append(current)
            current = next()
        }
        val word = buffer.toString()
        addToken(TokenType.WORD, word)
    }

    private fun tokenizeOperator() {
        val current = peek(0)
        clearBuffer()

        next()
        addToken(
            when (current) {
                in BEGIN_TOKENS -> TokenType.BEGIN
                in END_TOKENS -> TokenType.END
                in ASSIGN_TOKENS -> TokenType.COLON
                in SEPARATOR_TOKENS -> return // TokenType.COMMA
                else -> return
            }
        )
    }

    private fun tokenizeText() {
        next() // skip "
        clearBuffer()
        var current = peek(0)
        while (true) {
            if (current == '\\') {
                current = next()
                when (current) {
                    // TODO: use map or anything?
                    '\\' -> {
                        current = next()
                        buffer.append('\\')
                        continue
                    }

                    '"' -> {
                        current = next()
                        buffer.append('"')
                        continue
                    }

                    '0' -> {
                        current = next()
                        buffer.append('\u0000')
                        continue
                    }

                    'b' -> {
                        current = next()
                        buffer.append('\b')
                        continue
                    }

                    'f' -> {
                        current = next()
                        buffer.append('\u000c')
                        continue
                    }

                    'n' -> {
                        current = next()
                        buffer.append('\n')
                        continue
                    }

                    'r' -> {
                        current = next()
                        buffer.append('\r')
                        continue
                    }

                    't' -> {
                        current = next()
                        buffer.append('\t')
                        continue
                    }

                    'u' -> {
                        val rollbackPosition = pos
                        while (current == 'u') current = next()

                        var buffUtf = ""
                        while (isHexNumber(current)) {
                            buffUtf += current
                            current = next()
                        }
                        if (buffUtf.isNotEmpty()) {
                            buffer.append(String(intArrayOf(buffUtf.toInt(16)), 0, 1))
                        } else {
                            buffer.append("\\u")
                            pos = rollbackPosition
                        }
                        continue
                    }
                }
                buffer.append('\\')
                continue
            }
            if (current == '"') break
            if (current == '\u0000') throw Exception("Reached end of file while parsing text string.")
            buffer.append(current)
            current = next()
        }
        next() // skip closing "
        addToken(TokenType.STR, buffer.toString())
    }

    private fun tokenizeNumber() {
        clearBuffer()
        var current = peek(0)
        if (current == '-') {
            buffer.append(current)
            current = next()
        }
        if (current == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            next()
            next()
            tokenizeHexNumber()
            return
        }

        while (true) {
            if (current == '.') {
                if (buffer.indexOf(".") != -1) throw Exception("Invalid float number")
            } else if (!Character.isDigit(current)) {
                break
            }
            buffer.append(current)
            current = next()
        }
        addToken(TokenType.NUM, buffer.toString())
    }

    private fun tokenizeHexNumber() {
        clearBuffer()
        var current = peek(0)
        while (isHexNumber(current) || current == '_') {
            if (current != '_') buffer.append(current) // allow _ symbol
            current = next()
        }
        if (buffer.length > 0) addToken(TokenType.HEX, buffer.toString())
    }

    private fun isHexNumber(current: Char) = Character.isDigit(current) || current in 'a'..'f' || current in 'A'..'F'

    private fun clearBuffer() {
        buffer.setLength(0)
    }

    protected fun next(): Char {
        pos++
        val result = peek(0)
        if (result == '\n') {
            row++
            col = 1
        } else col++
        return result
    }

    protected fun peek(relPos: Int): Char {
        val currentPos = pos + relPos
        return if (currentPos >= length) '\u0000' else input[currentPos]
    }

    private fun addToken(type: ITokenType, text: String = "") {
        tokens.add(Token(type, text, row, col))
    }

    class Token(val type: ITokenType, val text: String, val row: Int, val col: Int) {
        fun position() = "[$row $col]"
        override fun toString() = type.name + " " + position() + " " + text
    }

    open class FxmlNode(var parent: FxmlNode? = null) {
        var params: MutableMap<String, Any> = mutableMapOf()
        var childs: MutableList<Any> = mutableListOf()

        constructor(f: FxmlNode, parent: FxmlNode?) : this(parent) {
            this.params = f.params
            this.childs = f.childs
        }

        fun stepSpaces(n: Int) = " ".repeat(4 * n)

        override fun toString() = toStr(0, false)

        fun toStr(spacing: Int, isChild: Boolean, minify: Boolean = false): String {
            val sb = StringBuilder()

            if (minify) sb.append("(")
            else sb.append((if (isChild) stepSpaces(spacing) else "") + "(" + '\n')
            if (params.keys.size > 0) {
                params2string(sb, spacing + 1, minify)
                if (minify) sb.append((if (childs.size > 0) "," else ""))
                else sb.append((if (childs.size > 0) ",\n" else "") + '\n')
            }
            if (childs.size > 0) {
                childs2str(sb, spacing + 1, minify)
                if (!minify) sb.append('\n')
            }

            if (minify) sb.append(")")
            else sb.append(stepSpaces(spacing) + ")")

            return sb.toString()
        }


        fun params2string(sb: StringBuilder, spacing: Int, minify: Boolean) {
            for (key in params.keys) {
                if (params.keys.indexOf(key) != 0) {
                    if (minify) sb.append(",")
                    else sb.append(",\n")
                }
                if (!minify) sb.append(stepSpaces(spacing))
                escapeString(key, sb)
                sb.append(if (minify) ":" else ": ")
                if (params[key] is FxmlNode) sb.append((params[key] as FxmlNode).toStr(spacing, false, minify))
                else escapeString(params[key].toString(), sb)
            }
        }

        private fun childs2str(sb: StringBuilder, spacing: Int, minify: Boolean) {
            for (i in childs.indices) {
                if (i != 0) {
                    if (minify) sb.append(",")
                    else sb.append(",\n")
                }
                if (childs[i] is FxmlNode) {
                    sb.append((childs[i] as FxmlNode).toStr(spacing, true, minify))
                } else {
                    if (!minify) sb.append(stepSpaces(spacing))
                    escapeString(childs[i].toString(), sb)
                }
            }
        }

        open operator fun get(s: String): FxmlNode {
            return when (val tmp = params[s]) {
                is FxmlNode -> tmp
                else ->
                    if (parent?.has(s) == true) return parent!![s] else FxmlNode()
            }
        }

        operator fun get(i: Int) = try {
            when (val tmp = childs[i]) {
                is FxmlNode -> tmp
                else -> FxmlNode()
            }
        } catch(_: Exception) {
            FxmlNode()
        }

        fun str(s: String, d: String = ""): String {
            try {
                if (params.containsKey(s)) return params[s].let { if (it is String) it else d }
                if (parent?.has(s) == true) return parent!!.str(s, d)
            } catch (e: Exception) {
                Log.d("getStrI", e.stackTraceToString())
            }
            return d
        }

        fun str(i: Int, d: String = ""): String {
            try {
                if (childs.size > i) return childs[i].let { if (it is String) it else d }
                if (parent?.has(i) == true) return parent!!.str(i)
            } catch (e: Exception) {
                Log.d("getStrI", e.stackTraceToString())
            }
            return d
        }

        fun num(s: String, d: Int = 0): Int {
            when (params[s]) {
                is String -> try {
                    if ((params[s] as String).isNotEmpty()) return (params[s] as String).toInt()
                } catch (e: Exception) {
                    Log.d("parseInt", e.stackTraceToString())
                }
            }
            if (parent?.has(s) == true) return parent!!.num(s, d)
            return d
        }

        fun float(s: String, d: Float = 0f): Float {
            when (params[s]) {
                is String -> try {
                    (params[s] as String).apply { if (isNotEmpty()) return toFloat() }
                } catch (e: Exception) {
                    Log.d("parseFloat", e.stackTraceToString())
                }
            }
            if (parent?.has(s) == true) return parent!!.float(s, d)
            return d
        }

        fun bool(s: String) =
            (if (has(s)) str(s).run { listOf("1", "true", "yes").contains(lowercase().trim()) } else false)

        fun childCount() = childs.size

        fun paramCount() = params.size

        fun has(k: String): Boolean {
            return params.containsKey(k) || parent?.has(k) == true
        }

        fun has(i: Int): Boolean {
            return (childs.size > i && (childs[i] is FxmlNode || childs[i] is String)) || parent?.has(i) == true
        }

        operator fun set(i: Int, value: Any) {
            if (i >= childs.size) for (p in childs.size..i) childs.add("")
            childs[i] = value
        }

        operator fun set(pos: String, value: Any) {
            params[pos] = value
        }

        fun append(data: FxmlNode) {
            Log.w("data", data.toString())
            Log.w("this", this.toString())
            params += data.params
            childs = data.childs
            Log.w("changed", this.toString())
        }

        fun escapeString(data: String, sb: StringBuilder = StringBuilder()): StringBuilder {
            if (data.isEmpty()) return sb.append("\"\"")

            sb.append('"')

            data.forEach { c ->
                sb.append(
                    escapeMap[c] ?: if (c < ' ' || c > 0x7f.toChar()) "\\u" + (Integer.toHexString(c.code)
                        .padStart(4, '0'))
                    else c
                )
            }

            sb.append('"')
            return sb
        }
    }

    private fun warn(msg: String) {/*val r = tokens[tokenPos].row
        val c = tokens[tokenPos].col*/
        Log.w("LayoutParser", /*"[$r:$c] $msg"*/msg)
        warnings.add(ParseWarning(msg, /*r, c*/0, 0))
    }

    class ParseWarning(val msg: String, val line: Int, val col: Int) {
        override fun toString() = "[$line:$col] Warning: $msg"
    }
}