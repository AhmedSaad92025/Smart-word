#!/usr/bin/env python3
"""Builds three hand-written DOCX files for trying Smart Word on a phone.

They are written from raw XML, not with the app's DOCX engine, so they are independent test input.
usage: make_test_docs.py <output-dir>
"""
import re
import sys
import zipfile
from xml.sax.saxutils import escape

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
A = "http://schemas.openxmlformats.org/drawingml/2006/main"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
DECL = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'


PPR_ORDER = ["pStyle", "keepNext", "keepLines", "pageBreakBefore", "widowControl", "numPr", "pBdr", "shd", "tabs",
             "bidi", "spacing", "ind", "contextualSpacing", "jc", "outlineLvl"]
RPR_ORDER = ["rStyle", "rFonts", "b", "bCs", "i", "iCs", "color", "sz", "szCs", "u", "rtl"]
_ELEMENT = re.compile(r"<w:(\w+)\b[^>]*/>|<w:(\w+)\b[^>]*>.*?</w:\2>", re.S)


def ordered(xml, order):
    """Sorts property elements into the order the schema requires (Word is strict about it)."""
    items = []
    for m in _ELEMENT.finditer(xml):
        name = m.group(1) or m.group(2)
        items.append((order.index(name) if name in order else len(order), m.group(0)))
    assert "".join(t for _, t in items) == xml, f"unparsed properties: {xml}"
    return "".join(t for _, t in sorted(items, key=lambda x: x[0]))


def r(text, rpr=""):
    props = f"<w:rPr>{ordered(rpr, RPR_ORDER)}</w:rPr>" if rpr else ""
    return f'<w:r>{props}<w:t xml:space="preserve">{escape(text)}</w:t></w:r>'


def ar(text, rpr=""):
    """An Arabic run: right-to-left with the complex-script variants of the properties."""
    return r(text, "<w:rtl/>" + rpr)


def p(inner, ppr=""):
    props = f"<w:pPr>{ordered(ppr, PPR_ORDER)}</w:pPr>" if ppr else ""
    return f"<w:p>{props}{inner}</w:p>"


def ap(text, ppr="", rpr=""):
    """An Arabic paragraph (w:bidi)."""
    return p(ar(text, rpr), ppr + "<w:bidi/>")


def num(num_id, lvl):
    return f'<w:numPr><w:ilvl w:val="{lvl}"/><w:numId w:val="{num_id}"/></w:numPr>'


def style(sid, name, based="Normal", ppr="", rpr="", typ="paragraph", extra=""):
    b = f'<w:basedOn w:val="{based}"/>' if based else ""
    pp = f"<w:pPr>{ordered(ppr, PPR_ORDER)}</w:pPr>" if ppr else ""
    rp = f"<w:rPr>{ordered(rpr, RPR_ORDER)}</w:rPr>" if rpr else ""
    return f'<w:style w:type="{typ}" w:styleId="{sid}"><w:name w:val="{name}"/>{b}{extra}{pp}{rp}</w:style>'


STYLES = (
    DECL + f'<w:styles xmlns:w="{W}">'
    '<w:docDefaults><w:rPrDefault><w:rPr>'
    '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Arial"/><w:sz w:val="24"/><w:szCs w:val="28"/>'
    '</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault>'
    '</w:docDefaults>'
    '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>'
    + style("Title", "Title", rpr='<w:b/><w:bCs/><w:sz w:val="48"/><w:szCs w:val="48"/>'
            '<w:color w:val="1F3864"/>', extra="<w:qFormat/>")
    + style("Heading1", "heading 1", ppr='<w:keepNext/><w:spacing w:before="360" w:after="120"/><w:outlineLvl w:val="0"/>',
            rpr='<w:b/><w:bCs/><w:color w:val="2F5496" w:themeColor="accent1" w:themeShade="BF"/>'
                '<w:sz w:val="36"/><w:szCs w:val="36"/>', extra="<w:qFormat/>")
    + style("Heading2", "heading 2", ppr='<w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="1"/>',
            rpr='<w:b/><w:bCs/><w:color w:val="2F5496" w:themeColor="accent1" w:themeShade="BF"/>'
                '<w:sz w:val="28"/><w:szCs w:val="28"/>', extra="<w:qFormat/>")
    + style("Strong", "Strong", based=None, typ="character", rpr="<w:b/><w:bCs/>")
    + style("Quote", "Quote", ppr='<w:ind w:left="720" w:right="720"/>', rpr='<w:i/><w:iCs/><w:color w:val="595959"/>')
    + '</w:styles>'
)

THEME = (
    DECL + f'<a:theme xmlns:a="{A}" name="Office"><a:themeElements><a:clrScheme name="Office">'
    '<a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1>'
    '<a:dk2><a:srgbClr val="44546A"/></a:dk2><a:lt2><a:srgbClr val="E7E6E6"/></a:lt2>'
    '<a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2>'
    '<a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4>'
    '<a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6>'
    '<a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme>'
    '<a:fontScheme name="Office"><a:majorFont><a:latin typeface="Calibri Light"/><a:ea typeface=""/><a:cs typeface=""/>'
    '<a:font script="Arab" typeface="Times New Roman"/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/>'
    '<a:ea typeface=""/><a:cs typeface=""/><a:font script="Arab" typeface="Arial"/></a:minorFont></a:fontScheme>'
    '</a:themeElements></a:theme>'
)


def lvl(i, fmt, text, left, hanging=360, extra=""):
    return (f'<w:lvl w:ilvl="{i}"><w:start w:val="1"/><w:numFmt w:val="{fmt}"/>{extra}'
            f'<w:lvlText w:val="{text}"/><w:lvlJc w:val="left"/>'
            f'<w:pPr><w:ind w:left="{left}" w:hanging="{hanging}"/></w:pPr></w:lvl>')


def abstract(i, *levels):
    return f'<w:abstractNum w:abstractNumId="{i}"><w:multiLevelType w:val="multilevel"/>' + "".join(levels) + "</w:abstractNum>"


NUMBERING = (
    DECL + f'<w:numbering xmlns:w="{W}">'
    + abstract(0, lvl(0, "decimal", "%1.", 720), lvl(1, "lowerLetter", "%2)", 1440), lvl(2, "lowerRoman", "%3.", 2160))
    + abstract(1, lvl(0, "decimal", "%1.", 720), lvl(1, "decimal", "%1.%2", 1440), lvl(2, "decimal", "%1.%2.%3", 2160))
    + abstract(2, lvl(0, "arabicAbjad", "%1-", 720), lvl(1, "hindiNumbers", "%1.%2", 1440), lvl(2, "arabicAlpha", "%3.", 2160))
    + abstract(3, lvl(0, "bullet", "•", 720), lvl(1, "bullet", "◦", 1440))
    + '<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>'
    '<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>'
    '<w:num w:numId="3"><w:abstractNumId w:val="2"/></w:num>'
    '<w:num w:numId="4"><w:abstractNumId w:val="3"/></w:num>'
    '<w:num w:numId="5"><w:abstractNumId w:val="0"/><w:lvlOverride w:ilvl="0"><w:startOverride w:val="5"/></w:lvlOverride></w:num>'
    '</w:numbering>'
)


def build(path, body):
    sect = ('<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
            '<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>')
    ct = ("http://schemas.openxmlformats.org/officeDocument/2006/relationships")
    types = (
        DECL + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
        '<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>'
        '<Override PartName="/word/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>'
        '</Types>'
    )
    root_rels = (DECL + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                 f'<Relationship Id="rId1" Type="{REL}/officeDocument" Target="word/document.xml"/></Relationships>')
    doc_rels = (DECL + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                f'<Relationship Id="rId1" Type="{REL}/styles" Target="styles.xml"/>'
                f'<Relationship Id="rId2" Type="{REL}/numbering" Target="numbering.xml"/>'
                f'<Relationship Id="rId3" Type="{REL}/theme" Target="theme/theme1.xml"/></Relationships>')
    document = DECL + f'<w:document xmlns:w="{W}"><w:body>{body}{sect}</w:body></w:document>'
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", types)
        z.writestr("_rels/.rels", root_rels)
        z.writestr("word/document.xml", document)
        z.writestr("word/_rels/document.xml.rels", doc_rels)
        z.writestr("word/styles.xml", STYLES)
        z.writestr("word/numbering.xml", NUMBERING)
        z.writestr("word/theme/theme1.xml", THEME)


def note(text):
    """A short Arabic note in gray telling what the tester should see."""
    return ap("المتوقع: " + text, ppr='<w:pBdr><w:bottom w:val="single" w:sz="6" w:space="4" w:color="A5A5A5"/></w:pBdr>',
              rpr='<w:i/><w:iCs/><w:color w:val="595959"/><w:sz w:val="20"/><w:szCs w:val="20"/>')


def doc_arabic():
    b = [
        ap("تقرير تجريبي بالعربية", ppr='<w:pStyle w:val="Title"/><w:jc w:val="center"/>'),
        note("عنوان كبير أزرق داكن في المنتصف، ثم النص كله يبدأ من اليمين."),
        ap("المقدمة", ppr='<w:pStyle w:val="Heading1"/>'),
        ap("هذه فقرة عربية عادية تُكتب من اليمين إلى اليسار. الغرض منها التأكد من أن التطبيق يعرض النص العربي "
           "بالاتجاه الصحيح، وأن نهاية السطر تقع على اليسار، وأن علامات الترقيم مثل النقطة والفاصلة تظهر في أماكنها."),
        ap("فقرة مضبوطة (توزيع) على الجانبين: يجب أن تمتد أسطرها من الهامش الأيمن حتى الأيسر بالتساوي، ما عدا السطر "
           "الأخير فيبقى عند اليمين. نكرر هذا الكلام قليلًا ليصبح طويلًا بما يكفي ليظهر الفرق بين الأسطر المتعددة في "
           "الشاشة الضيقة للهاتف، ويتأكد القارئ من أن التوزيع يعمل.", ppr='<w:jc w:val="both"/>'),
        ap("العنوان الفرعي الأول", ppr='<w:pStyle w:val="Heading2"/>'),
        p(ar("نص ") + ar("عريض", "<w:b/><w:bCs/>") + ar(" ونص ") + ar("مائل", "<w:i/><w:iCs/>") + ar(" ونص ")
          + ar("تحته خط", '<w:u w:val="single"/>') + ar(" ونص ") + ar("عريض ومائل معًا", "<w:b/><w:bCs/><w:i/><w:iCs/>")
          + ar(" في فقرة واحدة."), "<w:bidi/>"),
        p(ar("جملة عربية فيها كلمات إنجليزية مثل ") + r("Microsoft Word") + ar(" و") + r("DOCX 2026")
          + ar(" وأرقام مثل 12345 داخل النص."), "<w:bidi/>"),
        note("الكلمات الإنجليزية تبقى بترتيبها الصحيح (من اليسار لليمين) وسط النص العربي."),
        ap("العنوان الفرعي الثاني", ppr='<w:pStyle w:val="Heading2"/>'),
        ap("اقتباس بمسافة بادئة من الجانبين: يجب أن يظهر مائلًا رماديًا ومنسحبًا عن الهامشين.", ppr='<w:pStyle w:val="Quote"/>'),
        p(r("This paragraph is English (left to right) inside an Arabic document. It must start at the left edge."), ""),
        p(r("This English paragraph is right-aligned on purpose."), '<w:jc w:val="right"/>'),
        p(ar("فقرة عربية محاذاة اليسار عمدًا"), '<w:bidi/><w:jc w:val="right"/>'),
        note("السطر الأخير: فقرة عربية طُلب لها «يسار» في وورد، فيجب أن تظهر على اليسار."),
        ap("الخاتمة", ppr='<w:pStyle w:val="Heading1"/>'),
        ap("انتهى المستند. إذا ظهرت العناوين بحجمها الصحيح والنص بالاتجاه الصحيح فالعرض سليم."),
    ]
    return "".join(b)


def doc_lists():
    items = lambda *xs: "".join(xs)
    ai = lambda text, num_id, lv: ap(text, ppr=num(num_id, lv))
    li = lambda text, num_id, lv: p(r(text), num(num_id, lv))
    b = [
        ap("اختبار القوائم", ppr='<w:pStyle w:val="Title"/>'),
        ap("1) قائمة عربية متعددة المستويات", ppr='<w:pStyle w:val="Heading1"/>'),
        note("أ- ثم أ.١ و أ.٢ ثم ب- ثم ب.١ ثم تعود أ. ب. للمستوى الثالث؛ الترقيم على اليمين."),
        ai("البند الأول", 3, 0), ai("بند فرعي أول", 3, 1), ai("بند فرعي ثانٍ", 3, 1), ai("بند فرعي ثالث", 3, 1),
        ai("البند الثاني", 3, 0), ai("بند فرعي يبدأ من جديد", 3, 1), ai("بند من المستوى الثالث", 3, 2),
        ai("البند الثالث", 3, 0),
        ap("2) قائمة إنجليزية بأرقام متداخلة", ppr='<w:pStyle w:val="Heading1"/>'),
        note("1. ثم 1.1 ثم 1.1.1 ثم 1.1.2 ثم 1.2 ثم 2. ثم 2.1؛ الأرقام تعيد البدء بعد كل مستوى أعلى."),
        li("Chapter one", 2, 0), li("Section one point one", 2, 1), li("Subsection 1.1.1", 2, 2),
        li("Subsection 1.1.2", 2, 2), li("Section one point two", 2, 1), li("Chapter two", 2, 0),
        li("Section two point one", 2, 1),
        ap("3) أرقام وحروف ثم نقاط", ppr='<w:pStyle w:val="Heading1"/>'),
        note("1. 2. ثم a) b) ثم i. ثم 3. ثم نقاط ● ثم ◦."),
        li("First", 1, 0), li("Second", 1, 0), li("Second, part a", 1, 1), li("Second, part b", 1, 1),
        li("Deep item", 1, 2), li("Third", 1, 0),
        li("Bullet one", 4, 0), li("Bullet two", 4, 0), li("Sub-bullet", 4, 1),
        ap("4) قائمة تبدأ من الرقم 5", ppr='<w:pStyle w:val="Heading1"/>'),
        note("هذه القائمة تبدأ بالرقم 5 ثم 6 (وليس 1)."),
        li("Starts at five", 5, 0), li("Then six", 5, 0),
    ]
    return "".join(b)


def doc_format():
    def lab(text):
        return p(r(text, '<w:b/><w:color w:val="595959"/><w:sz w:val="20"/>'), '<w:keepNext/>')
    big = "نص عربي للتجربة"
    b = [
        p(r("Colors, fonts and alignment"), '<w:pStyle w:val="Title"/>'),
        p(r("Fonts"), '<w:pStyle w:val="Heading1"/>'),
        p(r("Calibri (default sans) - The quick brown fox", '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>'), ""),
        p(r("Arial (sans) - The quick brown fox", '<w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>'), ""),
        p(r("Times New Roman (serif) - The quick brown fox", '<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>'), ""),
        p(r("Courier New (monospace) - The quick brown fox", '<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/>'), ""),
        p(r("Sizes: ") + r("8pt ", '<w:sz w:val="16"/>') + r("11pt ", '<w:sz w:val="22"/>') + r("16pt ", '<w:sz w:val="32"/>')
          + r("24pt", '<w:sz w:val="48"/>'), ""),
        p(r("Colors"), '<w:pStyle w:val="Heading1"/>'),
        p(r("Red ", '<w:color w:val="C00000"/>') + r("Green ", '<w:color w:val="00B050"/>') + r("Blue ", '<w:color w:val="0070C0"/>')
          + r("Purple ", '<w:color w:val="7030A0"/>') + r("Gray", '<w:color w:val="7F7F7F"/>'), ""),
        p(r("Theme color Accent 2 (orange): ") + r("ED7D31 from the theme", '<w:b/><w:color w:val="ED7D31" w:themeColor="accent2"/>'), ""),
        p(r("Theme Accent 6 (green): ") + r("70AD47 from the theme", '<w:b/><w:color w:themeColor="accent6"/>'), ""),
        p(r("Accent 1 lighter 60%: ") + r("B4C7E7 on white", '<w:color w:themeColor="accent1" w:themeTint="66"/>')
          + r(" / darker 50%: ") + r("203864", '<w:color w:themeColor="accent1" w:themeShade="80"/>'), ""),
        p(r("Style combinations"), '<w:pStyle w:val="Heading1"/>'),
        p(r("Bold ", "<w:b/>") + r("Italic ", "<w:i/>") + r("Underline ", '<w:u w:val="single"/>')
          + r("Bold+Italic+Underline ", '<w:b/><w:i/><w:u w:val="single"/>')
          + r("Large bold red", '<w:b/><w:color w:val="C00000"/><w:sz w:val="32"/>'), ""),
        p(r("Strong character style on normal text: ") + r("this is strong", '<w:rStyle w:val="Strong"/>'), ""),
        p(r("Heading 2 with a Strong run: ") + r("this part uses Strong", '<w:rStyle w:val="Strong"/>'), '<w:pStyle w:val="Heading2"/>'),
        p(r("(In Word the Strong part is NOT bold here: bold from the heading style and bold from Strong cancel out.)",
            '<w:i/><w:color w:val="595959"/><w:sz w:val="20"/>'), ""),
        p(r("Alignment (English)"), '<w:pStyle w:val="Heading1"/>'),
        lab("Left"), p(r("Left aligned paragraph text."), '<w:jc w:val="left"/>'),
        lab("Center"), p(r("Centered paragraph text."), '<w:jc w:val="center"/>'),
        lab("Right"), p(r("Right aligned paragraph text."), '<w:jc w:val="right"/>'),
        lab("Justified"),
        p(r("Justified paragraph text that is long enough to wrap over several lines on a phone screen so that the "
            "even spacing between both edges becomes visible; the last line stays on the left side as in Word."),
          '<w:jc w:val="both"/>'),
        ap("المحاذاة بالعربية", ppr='<w:pStyle w:val="Heading1"/>'),
        lab("الافتراضي (يمين)"), ap(big),
        lab("وسط"), ap(big, ppr='<w:jc w:val="center"/>'),
        lab("«يسار» في وورد (تُحفظ right في الملف)"), p(ar(big), '<w:bidi/><w:jc w:val="right"/>'),
        lab("توزيع"),
        ap("نص عربي طويل نسبيًا لاختبار التوزيع على الجانبين في شاشة الهاتف الضيقة، يجب أن تتساوى المسافات بين الكلمات "
           "في كل الأسطر ما عدا الأخير.", ppr='<w:jc w:val="both"/>'),
        p(r("Arabic colors and sizes"), '<w:pStyle w:val="Heading1"/>'),
        p(ar("أحمر ", '<w:color w:val="C00000"/>') + ar("أخضر ", '<w:color w:val="00B050"/>')
          + ar("كبير ", '<w:sz w:val="40"/><w:szCs w:val="40"/>') + ar("عريض", "<w:b/><w:bCs/>"), "<w:bidi/>"),
    ]
    return "".join(b)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    build(f"{out}/01-arabic-rtl-headings.docx", doc_arabic())
    build(f"{out}/02-multilevel-lists.docx", doc_lists())
    build(f"{out}/03-colors-fonts-alignment.docx", doc_format())
    print("done")
