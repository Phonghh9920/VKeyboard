package com.vladgba.keyb

class LayoutParse(val input: String, val c: KeybCtl) {
    private val OPERATOR_CHARS = "{}[](),:"

    enum class TokenType {
        WORD,
        COLON,
        STR,
        NUM,
        BEGIN,
        END,
        HEX,
    }

    private var pos = 0
    private val length = input.length
    private var row = 1
    private var col = 1

    var tab = ""

    var tokenPos = 0
    private val tokens: MutableList<Token> = mutableListOf()
    private val buffer: StringBuilder = StringBuilder()
    private val parseErrors: MutableList<String> = mutableListOf()
    private var result = DataNode()

    var tokenCount = 0

    fun parse(): DataNode {
        if (tokens.size == 0) tokenize()
        parseErrors.clear()
        enumerate(result)
        c.log(result.toString())
        return if (tokens[0].type == TokenType.BEGIN) result[0] else result
    }

    private fun enumerate(v: DataNode) {
        while (tokenPos < tokenCount) {
            if (detectedEnd()) break
            when (tokens[tokenPos].type) {
                TokenType.WORD, TokenType.STR, TokenType.HEX, TokenType.NUM -> if (tokens[tokenPos + 1].type == TokenType.COLON) assign(v) else append(v)
                TokenType.BEGIN -> {
                    append(v)
                }
                else -> {
                    tokenPos++ // ??? symbol
                }
            }
        }
    }

    private fun detectedEnd() = tokens[tokenPos].type == TokenType.END

    private fun append(v: DataNode) {
        if (isValue(tokenPos)) {
            v.childs.add(tokens[tokenPos].text)
            tokenPos++ // DataNode
        } else if (tokens[tokenPos].type == TokenType.BEGIN) {
            val newNode = DataNode()
            v.childs.add(newNode)
            tokenPos++ // "("
            enumerate(newNode)
            tokenPos++ // ")"
        } else {
            tokenPos++ // ??? symbol
        }
    }

    private fun assign(v: DataNode) {
        if (isValue(tokenPos + 2)) {
            v.params.put(tokens[tokenPos].text, tokens[tokenPos+2].text)
            tokenPos+=3 // "key" ":" "value"
        } else if (tokens[tokenPos + 2].type == TokenType.BEGIN) {
            val newNode = DataNode()
            v.params.put(tokens[tokenPos].text, newNode)
            tokenPos+=3 // "key" ":" "("
            enumerate(newNode)
            tokenPos++ // ")"
        } else {
            tokenPos++
        }
    }

    private fun isValue(i: Int) = tokens[i].type == TokenType.STR || tokens[i].type == TokenType.WORD || tokens[i].type == TokenType.NUM || tokens[i].type == TokenType.HEX

    fun tokenize(): List<Token?> {
        while (pos < length) {
            val current: Char = peek(0)
            if (Character.isDigit(current)) tokenizeNumber()
            else if (isIdentifierStart(current)) tokenizeWord()
            else if (current == '"') tokenizeText()
            else if (current == '#') tokenizeComment()
            else if (OPERATOR_CHARS.indexOf(current) != -1) tokenizeOperator()
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
        var current = peek(0)
        clearBuffer()

        next()
        addToken(
            when (current) {
                '(', '[', '{' -> TokenType.BEGIN
                ')', ']', '}' -> TokenType.END
                ':' -> TokenType.COLON
                ',' -> return // TokenType.COMMA
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
                        var thex = ""
                        while (current == 'u') current = next()
                        /*
                        while (true) {
                            val cp = next()
                            if (isHexNumber(cp)) thex += cp
                            else break
                        }
                        buffer.append("\\u" + thex)*/


                        var escapedValue = 0
                        var i = 12
                        while (i >= 0 && escapedValue != -1) {
                            escapedValue = if (isHexNumber(current)) {
                                escapedValue or (Character.digit(current, 16) shl i)
                            } else {
                                -1
                            }
                            current = next()
                            i -= 4
                        }
                        if (escapedValue >= 0) {
                            buffer.append(Character.toChars(escapedValue))
                        } else {
                            // rollback
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
            if (current != '_') {
                // allow _ symbol
                buffer.append(current)
            }
            current = next()
        }
        val length = buffer.length
        if (length > 0) {
            addToken(TokenType.HEX, buffer.toString())
        }
    }

    private fun isHexNumber(current: Char): Boolean {
        return Character.isDigit(current) || current in 'a'..'f' || current in 'A'..'F'
    }


    private fun clearBuffer() {
        buffer.setLength(0)
    }

    private fun next(): Char {
        pos++
        val result = peek(0)
        if (result == '\n') {
            row++
            col = 1
        } else col++
        return result
    }

    private fun peek(relPos: Int): Char {
        val currentPos = pos + relPos
        return if (currentPos >= length) '\u0000' else input[currentPos]
    }

    private fun addToken(type: TokenType) {
        addToken(type, "")
    }

    private fun addToken(type: TokenType, text: String) {
        tokens.add(Token(type, text, row, col))
    }

    class Token(val type: TokenType, val text: String, val row: Int, val col: Int) {

        fun position(): String {
            return "[$row $col]"
        }

        override fun toString(): String {
            return type.name + " " + position() + " " + text
        }
    }

    class DataNode {
        val params: MutableMap<String, Any> = mutableMapOf()
        val childs: MutableList<Any> = mutableListOf()
        override fun toString(): String {
            val paramsData = StringBuilder()
            val childsData = StringBuilder()
            
            for (key in params.keys) paramsData.append((if (params.keys.indexOf(key) == 0) "" else ", ") + key + ": " + params[key].toString())
            for (i in childs.indices) childsData.append((if (i == 0) "" else ", ") + childs[i].toString())
            
            if (paramsData.length == 0) {
                if (childsData.length == 0) return "()"
                else return "([$childsData])"
            } else {
                if (childsData.length == 0) return "($paramsData)"
                else return "($paramsData, [$childsData])"

            }
        }

        operator fun get(s: String): DataNode {
            val tmp = params[s]
            return when (tmp) {
                is DataNode -> tmp
                else -> DataNode()
            }
        }

        operator fun get(i: Int): DataNode {
            val tmp = childs[i]
            return when (tmp) {
                is DataNode -> tmp
                else -> DataNode()
            }
        }

        fun str(s: String): String {
            val tmp = params[s]
            return when(tmp) {
                is String -> tmp
                else -> ""
            }
        }

        fun str(i: Int): String {
            val tmp = childs[i]
            return when(tmp) {
                is String -> tmp
                else -> ""
            }
        }

        fun num(s: String): Int {
            return when (params[s]) {
                is String -> try {
                    if ((params[s] as String).isNotEmpty()) (params[s] as String).toInt() else 0
                } catch (_: Exception) {
                    0
                }
                else -> 0
            }
        }

        fun bool(s: String) = when (params[s]) {
            is String -> (params[s] as String).trim() == "1"
            else -> false
        }

        fun count() = childs.size

        fun has(k: String) = params.containsKey(k)

    }
}