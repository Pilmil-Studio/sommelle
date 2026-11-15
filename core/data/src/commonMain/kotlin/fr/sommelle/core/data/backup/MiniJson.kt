package fr.sommelle.core.data.backup

/**
 * JSON minimal, sans dépendance : objets, listes, textes, entiers, booléens et null.
 * Suffisant pour le document de sauvegarde ; tout ce qui sort de ce cadre est refusé.
 */
internal object MiniJson {

    class ParseException(message: String) : Exception(message)

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Int -> out.append(value)
            is Long -> out.append(value)
            is String -> appendString(out, value)
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    appendString(out, key as String)
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
            else -> throw IllegalArgumentException("type non pris en charge : ${value::class}")
        }
    }

    private fun appendString(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    /** Renvoie Map, List, String, Long, Boolean ou null. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value(depth = 0)
        parser.skipSpaces()
        if (!parser.atEnd()) throw ParseException("texte en trop après le document")
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipSpaces() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("imbrication trop profonde")
            skipSpaces()
            if (atEnd()) throw ParseException("document tronqué")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> list(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseException("caractère inattendu « $c »")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("valeur invalide")
            i += word.length
            return result
        }

        private fun number(): Long {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toLongOrNull() ?: throw ParseException("nombre invalide")
        }

        private fun string(): String {
            i++ // guillemet ouvrant
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("texte tronqué")
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("texte tronqué")
                        when (val e = s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("séquence unicode tronquée")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw ParseException("séquence unicode invalide")
                                out.append(code.toChar())
                                i += 4
                            }
                            else -> throw ParseException("échappement invalide « $e »")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun list(depth: Int): List<Any?> {
            i++
            val items = mutableListOf<Any?>()
            skipSpaces()
            if (!atEnd() && s[i] == ']') {
                i++
                return items
            }
            while (true) {
                items += value(depth + 1)
                skipSpaces()
                if (atEnd()) throw ParseException("liste tronquée")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return items
                    else -> throw ParseException("liste mal formée")
                }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++
            val map = LinkedHashMap<String, Any?>()
            skipSpaces()
            if (!atEnd() && s[i] == '}') {
                i++
                return map
            }
            while (true) {
                skipSpaces()
                if (atEnd() || s[i] != '"') throw ParseException("clé attendue")
                val key = string()
                skipSpaces()
                if (atEnd() || s[i++] != ':') throw ParseException("« : » attendu")
                map[key] = value(depth + 1)
                skipSpaces()
                if (atEnd()) throw ParseException("objet tronqué")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw ParseException("objet mal formé")
                }
            }
        }
    }

    private const val MAX_DEPTH = 32
}
