package com.smartword.docx.model

import com.smartword.docx.xml.Ns
import com.smartword.docx.xml.XmlAttribute
import com.smartword.docx.xml.XmlElement

/**
 * Knows which prefix a document uses for the WordprocessingML namespace, so that
 * newly created elements and attributes are written the way the file already does.
 */
internal class WContext(val prefix: String) {

    /** Qualified name (element or attribute) in the WordprocessingML namespace. */
    fun qn(local: String): String = "$prefix:$local"

    fun el(local: String): XmlElement = XmlElement(qn(local), Ns.W)

    companion object {
        /** Finds the prefix bound to the WordprocessingML namespace on [root], declaring one if needed. */
        fun forRoot(root: XmlElement): WContext {
            var found: String? = null
            for (a in root.attributes) {
                if (a.name.startsWith("xmlns:") && a.value == Ns.W) {
                    val p = a.name.substring(6)
                    if (found == null || p == "w") found = p
                }
            }
            if (found != null) return WContext(found)

            val used = HashSet<String>()
            for (a in root.attributes) used.add(a.name)
            var candidate = "w"
            var n = 1
            while (used.contains("xmlns:$candidate")) {
                candidate = "w$n"
                n++
            }
            root.attributes.add(XmlAttribute("xmlns:$candidate", Ns.W))
            return WContext(candidate)
        }
    }
}
