package com.smartword.docx.xml

class XmlParseException(message: String, val position: Int) :
    Exception("$message (position $position)")

/**
 * A small, strict XML parser sufficient for OOXML parts.
 *
 * Deliberately unsupported: DOCTYPE declarations (rejected, which also rules out
 * entity-expansion and external-entity attacks), custom entities, and DTDs.
 */
object XmlParser {
    const val MAX_DEPTH = 256

    fun parse(bytes: ByteArray): XmlDocument = parse(decodeBytes(bytes))

    fun parse(text: String): XmlDocument = Impl(text).parseDocument()

    private fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private class Impl(private val s: String) {
        private var i = 0
        private val n = s.length

        private fun fail(message: String): Nothing = throw XmlParseException(message, i)

        fun parseDocument(): XmlDocument {
            if (n > 0 && s[0] == '﻿') i = 1
            if (s.startsWith("<?xml", i) && i + 5 < n && (s[i + 5].isWhitespace() || s[i + 5] == '?')) {
                val end = s.indexOf("?>", i)
                if (end < 0) fail("Unterminated XML declaration")
                i = end + 2
            }
            val prolog = ArrayList<XmlRaw>()
            while (true) {
                skipWhitespace()
                if (s.startsWith("<!--", i)) {
                    prolog.add(XmlRaw(readUntil("-->", "comment")))
                } else if (s.startsWith("<?", i)) {
                    prolog.add(XmlRaw(readUntil("?>", "processing instruction")))
                } else if (s.startsWith("<!DOCTYPE", i) || s.startsWith("<!doctype", i)) {
                    fail("DOCTYPE declarations are not allowed")
                } else {
                    break
                }
            }
            if (i >= n || s[i] != '<') fail("Document has no root element")
            val root = parseElementTree()

            val epilog = ArrayList<XmlRaw>()
            while (true) {
                skipWhitespace()
                if (i >= n) break
                if (s.startsWith("<!--", i)) {
                    epilog.add(XmlRaw(readUntil("-->", "comment")))
                } else if (s.startsWith("<?", i)) {
                    epilog.add(XmlRaw(readUntil("?>", "processing instruction")))
                } else {
                    fail("Unexpected content after the root element")
                }
            }
            val doc = XmlDocument(root)
            doc.prolog.addAll(prolog)
            doc.epilog.addAll(epilog)
            return doc
        }

        private fun skipWhitespace() {
            while (i < n) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++ else break
            }
        }

        private fun readUntil(terminator: String, what: String): String {
            val start = i
            val idx = s.indexOf(terminator, i)
            if (idx < 0) fail("Unterminated $what")
            i = idx + terminator.length
            return s.substring(start, i)
        }

        private fun isNameEnd(c: Char): Boolean =
            c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '/' || c == '>' || c == '='

        private fun parseElementTree(): XmlElement {
            val stack = ArrayList<XmlElement>()
            val scopes = ArrayList<Map<String, String>>()
            val baseScope: Map<String, String> = mapOf("xml" to Ns.XML)
            var root: XmlElement? = null

            while (true) {
                if (i >= n) fail("Unexpected end of document")
                if (s[i] != '<') {
                    val end = s.indexOf('<', i)
                    if (end < 0) fail("Unexpected end of document")
                    if (stack.isEmpty()) fail("Text outside the root element")
                    stack[stack.size - 1].children.add(XmlText(decodeText(i, end)))
                    i = end
                    continue
                }
                if (s.startsWith("</", i)) {
                    val close = s.indexOf('>', i)
                    if (close < 0) fail("Unterminated end tag")
                    val name = s.substring(i + 2, close).trim()
                    if (stack.isEmpty() || stack[stack.size - 1].name != name) {
                        fail("Mismatched end tag </$name>")
                    }
                    stack.removeAt(stack.size - 1)
                    scopes.removeAt(scopes.size - 1)
                    i = close + 1
                    if (stack.isEmpty()) break
                } else if (s.startsWith("<!--", i)) {
                    if (stack.isEmpty()) fail("Comment outside the root element")
                    stack[stack.size - 1].children.add(XmlRaw(readUntil("-->", "comment")))
                } else if (s.startsWith("<![CDATA[", i)) {
                    if (stack.isEmpty()) fail("CDATA outside the root element")
                    val end = s.indexOf("]]>", i + 9)
                    if (end < 0) fail("Unterminated CDATA section")
                    stack[stack.size - 1].children.add(XmlText(s.substring(i + 9, end), true))
                    i = end + 3
                } else if (s.startsWith("<?", i)) {
                    if (stack.isEmpty()) fail("Processing instruction outside the root element")
                    stack[stack.size - 1].children.add(XmlRaw(readUntil("?>", "processing instruction")))
                } else if (s.startsWith("<!", i)) {
                    fail("Unsupported markup declaration")
                } else {
                    val parentScope = if (scopes.isEmpty()) baseScope else scopes[scopes.size - 1]
                    val result = parseStartTag(parentScope)
                    val el = result.element
                    if (stack.isEmpty()) {
                        root = el
                    } else {
                        stack[stack.size - 1].children.add(el)
                    }
                    if (result.selfClosing) {
                        if (stack.isEmpty()) break
                    } else {
                        if (stack.size >= MAX_DEPTH) fail("Elements are nested too deeply")
                        stack.add(el)
                        scopes.add(result.scope)
                    }
                }
            }
            return root ?: fail("Document has no root element")
        }

        private class StartTag(
            val element: XmlElement,
            val selfClosing: Boolean,
            val scope: Map<String, String>,
        )

        private fun parseStartTag(parentScope: Map<String, String>): StartTag {
            i++ // skip '<'
            val nameStart = i
            while (i < n && !isNameEnd(s[i])) i++
            if (i == nameStart) fail("Empty element name")
            val el = XmlElement(s.substring(nameStart, i))
            var selfClosing = false

            while (true) {
                skipWhitespace()
                if (i >= n) fail("Unterminated start tag")
                val c = s[i]
                if (c == '>') {
                    i++
                    break
                }
                if (c == '/') {
                    if (i + 1 < n && s[i + 1] == '>') {
                        i += 2
                        selfClosing = true
                        break
                    }
                    fail("Malformed start tag")
                }
                val attrStart = i
                while (i < n && !isNameEnd(s[i])) i++
                if (i == attrStart) fail("Malformed attribute")
                val attrName = s.substring(attrStart, i)
                skipWhitespace()
                if (i >= n || s[i] != '=') fail("Attribute '$attrName' has no value")
                i++
                skipWhitespace()
                if (i >= n || (s[i] != '"' && s[i] != '\'')) fail("Attribute value must be quoted")
                val quote = s[i]
                i++
                val valueEnd = s.indexOf(quote, i)
                if (valueEnd < 0) fail("Unterminated attribute value")
                val value = decodeText(i, valueEnd)
                i = valueEnd + 1
                for (existing in el.attributes) {
                    if (existing.name == attrName) fail("Duplicate attribute '$attrName'")
                }
                el.attributes.add(XmlAttribute(attrName, value))
            }

            var scope = parentScope
            var own: HashMap<String, String>? = null
            for (a in el.attributes) {
                val key: String = (when {
                    a.name == "xmlns" -> ""
                    a.name.startsWith("xmlns:") -> a.name.substring(6)
                    else -> null
                }) ?: continue
                var target = own
                if (target == null) {
                    target = HashMap(parentScope)
                    own = target
                    scope = target
                }
                target[key] = a.value
            }
            el.namespaceUri = scope[el.prefix] ?: ""
            return StartTag(el, selfClosing, scope)
        }

        /** Decodes entity references and normalizes line endings for s[start, end). */
        private fun decodeText(start: Int, end: Int): String {
            var needsWork = false
            for (k in start until end) {
                val c = s[k]
                if (c == '&' || c == '\r') {
                    needsWork = true
                    break
                }
            }
            if (!needsWork) return s.substring(start, end)

            val sb = StringBuilder(end - start)
            var k = start
            while (k < end) {
                val c = s[k]
                if (c == '&') {
                    val semi = s.indexOf(';', k)
                    if (semi < 0 || semi >= end) {
                        i = k
                        fail("Unterminated entity reference")
                    }
                    appendEntity(sb, s.substring(k + 1, semi), k)
                    k = semi + 1
                } else if (c == '\r') {
                    sb.append('\n')
                    if (k + 1 < end && s[k + 1] == '\n') k++
                    k++
                } else {
                    sb.append(c)
                    k++
                }
            }
            return sb.toString()
        }

        private fun appendEntity(sb: StringBuilder, entity: String, position: Int) {
            when {
                entity == "lt" -> sb.append('<')
                entity == "gt" -> sb.append('>')
                entity == "amp" -> sb.append('&')
                entity == "quot" -> sb.append('"')
                entity == "apos" -> sb.append('\'')
                entity.startsWith("#x") || entity.startsWith("#X") -> appendCodePoint(sb, entity.substring(2).toIntOrNull(16), position)
                entity.startsWith("#") -> appendCodePoint(sb, entity.substring(1).toIntOrNull(10), position)
                else -> {
                    i = position
                    fail("Unknown entity '&$entity;'")
                }
            }
        }

        private fun appendCodePoint(sb: StringBuilder, codePoint: Int?, position: Int) {
            val valid = codePoint != null && (
                codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD ||
                    (codePoint in 0x20..0xD7FF) ||
                    (codePoint in 0xE000..0xFFFD) ||
                    (codePoint in 0x10000..0x10FFFF)
                )
            if (!valid || codePoint == null) {
                i = position
                fail("Invalid character reference")
            }
            sb.appendCodePoint(codePoint)
        }
    }
}
