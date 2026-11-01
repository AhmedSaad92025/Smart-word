#!/usr/bin/env python3
"""Opens engine-produced DOCX samples in LibreOffice and verifies them independently.

usage: check_compat.py <samples-dir> <output-dir>

For every <name>.docx:
  * converts it to UTF-8 text and checks that each line of <name>.expect appears;
  * converts it to PDF (proving the file opens and renders).
For styles-lists.docx it checks heading sizes, list labels and indents (including an Arabic list and
a style-inherited right-to-left alignment) and rendered colors against the engine's reading.
For rtl-layout.docx it also measures where each paragraph sits on the page, which
verifies how left/right alignment is interpreted in right-to-left paragraphs.
"""
import glob
import html
import os
import re
import subprocess
import sys

TOLERANCE = 6.0
MARGIN = 72.0  # 1440 twips

failures = []


def fail(message):
    failures.append(message)
    print(f"::error title=LibreOffice check::{message}")


def note(message):
    print(f"::notice title=LibreOffice check::{message}")


def convert(src, outdir, target):
    cmd = [
        "soffice", "-env:UserInstallation=file:///tmp/lo-profile", "--headless",
        "--convert-to", target, "--outdir", outdir, src,
    ]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=240)


def check_text(name, src, outdir):
    result = convert(src, outdir, "txt:Text (encoded):UTF8")
    out = os.path.join(outdir, name + ".txt")
    if result.returncode != 0 or not os.path.exists(out):
        fail(f"{name}: text conversion failed: {result.stderr.strip()[:300]}")
        return
    with open(out, encoding="utf-8-sig") as f:
        text = f.read()
    expect_path = os.path.join(os.path.dirname(src), name + ".expect")
    if not os.path.exists(expect_path):
        return
    with open(expect_path, encoding="utf-8") as f:
        expected = [line for line in f.read().split("\n") if line.strip()]
    missing = [line for line in expected if line not in text]
    if missing:
        fail(f"{name}: text missing in LibreOffice output: {missing}")
    else:
        note(f"{name}: all {len(expected)} expected text lines found")


def pdf_words(name, src, outdir):
    result = convert(src, outdir, "pdf")
    pdf = os.path.join(outdir, name + ".pdf")
    if result.returncode != 0 or not os.path.exists(pdf):
        fail(f"{name}: PDF conversion failed: {result.stderr.strip()[:300]}")
        return None
    boxes = pdf + ".html"
    subprocess.run(["pdftotext", "-bbox", pdf, boxes], check=True, timeout=120)
    with open(boxes, encoding="utf-8") as f:
        s = f.read()
    page = re.search(r'<page width="([\d.]+)"', s)
    width = float(page.group(1)) if page else 595.0
    words = []
    pattern = r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>'
    for x0, y0, x1, y1, text in re.findall(pattern, s):
        words.append((float(x0), float(y0), float(x1), float(y1), html.unescape(text)))
    return width, words


def row_extent(words, marker):
    hits = [w for w in words if w[4] == marker]
    if not hits:
        return None
    centre = (hits[0][1] + hits[0][3]) / 2
    row = [w for w in words if w[1] <= centre <= w[3]]
    return min(w[0] for w in row), max(w[2] for w in row)


def check_alignment(name, src, outdir):
    measured = pdf_words(name, src, outdir)
    if measured is None:
        return
    width, words = measured
    expectations = [
        ("TITLEMARK", "center"),
        ("RTLDEFAULT", "right"),
        ("RTLLEFT", "left"),
        ("RTLRIGHT", "right"),
        ("LTRDEFAULT", "left"),
        ("LTRRIGHT", "right"),
    ]
    for marker, side in expectations:
        extent = row_extent(words, marker)
        if extent is None:
            fail(f"{name}: marker {marker} not found in the PDF")
            continue
        x0, x1 = extent
        if side == "left":
            ok = abs(x0 - MARGIN) <= TOLERANCE
        elif side == "right":
            ok = abs(x1 - (width - MARGIN)) <= TOLERANCE
        else:
            ok = abs((x0 + x1) / 2 - width / 2) <= TOLERANCE * 2
        detail = f"{marker}: expected {side}, line spans {x0:.1f}..{x1:.1f} on a {width:.1f}pt page"
        if ok:
            note(detail)
        else:
            fail(detail)


def row_words(words, marker):
    """All words on the line of [marker], left to right."""
    hits = [w for w in words if w[4] == marker]
    if not hits:
        return None
    centre = (hits[0][1] + hits[0][3]) / 2
    return sorted((w for w in words if w[1] <= centre <= w[3]), key=lambda w: w[0])


def height_of(words, marker):
    hits = [w for w in words if w[4] == marker]
    return (hits[0][3] - hits[0][1]) if hits else None


def render_pixels(name, outdir):
    """First page as raw RGB pixels: (width, height, bytes)."""
    pdf = os.path.join(outdir, name + ".pdf")
    prefix = os.path.join(outdir, name + "-page")
    subprocess.run(["pdftoppm", "-r", "100", "-f", "1", "-l", "1", "-singlefile", "-ppm", pdf, prefix],
                   check=True, timeout=120)
    with open(prefix + ".ppm", "rb") as f:
        data = f.read()
    m = re.match(rb"P6\s+(\d+)\s+(\d+)\s+255\s", data)
    return int(m.group(1)), int(m.group(2)), data[m.end():]


def count_near(pixels, rgb, distance):
    _, _, raw = pixels
    n = 0
    r0, g0, b0 = rgb
    for i in range(0, len(raw) - 2, 3):
        dr, dg, db = raw[i] - r0, raw[i + 1] - g0, raw[i + 2] - b0
        if dr * dr + dg * dg + db * db <= distance * distance:
            n += 1
    return n


def check_styles(name, src, outdir):
    measured = pdf_words(name, src, outdir)
    if measured is None:
        return
    width, words = measured

    def expect(ok, detail):
        if ok:
            note(f"{name}: {detail}")
        else:
            fail(f"{name}: {detail}")

    # Heading sizes relative to the 11pt body text (20pt and 14pt headings).
    body = height_of(words, "BODYTEXT")
    for marker, points in (("HEADONE", 20.0), ("HEADTWO", 14.0)):
        h = height_of(words, marker)
        if h is None or body is None:
            fail(f"{name}: {marker} or BODYTEXT not found in the PDF")
            continue
        ratio = h / body
        wanted = points / 11.0
        expect(abs(ratio - wanted) <= wanted * 0.15,
               f"{marker} is {ratio:.2f}x the body text, expected about {wanted:.2f}x")

    # List labels, including the restart rules of multi-level lists.
    labels = [("NUMA", "1."), ("NUMB", "a)"), ("NUMC", "b)"), ("NUMD", "2."), ("NUME", "a)"),
              ("RSTA", "1."), ("RSTB", "a)"), ("RSTC", "2."), ("RSTD", "b)")]
    for marker, label in labels:
        row = row_words(words, marker)
        if row is None:
            fail(f"{name}: marker {marker} not found in the PDF")
            continue
        found = [w[4] for w in row]
        expect(label in found, f"{marker} line shows {found}, expected label {label}")

    # Indents of an ordinary list: label at 72+18, text at 72+36; second level 36 further.
    for marker, label_x, text_x in (("NUMA", 90.0, 108.0), ("NUMB", 126.0, 144.0)):
        row = row_words(words, marker)
        if row is None:
            continue
        text = [w for w in row if w[4] == marker][0]
        label = row[0]
        expect(abs(label[0] - label_x) <= TOLERANCE and abs(text[0] - text_x) <= TOLERANCE,
               f"{marker} label starts at {label[0]:.1f} (expected {label_x}), text at {text[0]:.1f} (expected {text_x})")

    # Right-to-left list: the label hangs on the right (start side), the text follows 18pt further in.
    for marker in ("ARLISTA", "ARLISTB"):
        row = row_words(words, marker)
        if row is None or len(row) < 3:
            fail(f"{name}: {marker} line has too few words: {row}")
            continue
        by_right = sorted(row, key=lambda w: -w[2])
        label_right, text_right = by_right[0][2], by_right[1][2]
        expect(abs(label_right - (width - 90.0)) <= TOLERANCE and abs(text_right - (width - 108.0)) <= TOLERANCE,
               f"{marker}: label ends at {label_right:.1f} (expected {width - 90.0:.1f}), "
               f"text at {text_right:.1f} (expected {width - 108.0:.1f})")

    # A style that sets "left" in a right-to-left paragraph puts the line on the right.
    extent = row_extent(words, "STYLERTL")
    if extent is None:
        fail(f"{name}: STYLERTL not found in the PDF")
    else:
        expect(abs(extent[1] - (width - MARGIN)) <= TOLERANCE,
               f"STYLERTL line ends at {extent[1]:.1f}, expected {width - MARGIN:.1f} (style-inherited start alignment)")

    # Colors survive: the theme-colored run and the shaded heading color are really drawn.
    pixels = render_pixels(name, outdir)
    orange = count_near(pixels, (0xED, 0x7D, 0x31), 40)
    blue = count_near(pixels, (0x2F, 0x54, 0x96), 45)
    expect(orange >= 30, f"theme color accent2 (ED7D31) drawn with {orange} pixels")
    expect(blue >= 100, f"heading color 2F5496 (accent1 shade) drawn with {blue} pixels")


def main():
    samples, outdir = sys.argv[1], sys.argv[2]
    os.makedirs(outdir, exist_ok=True)
    files = sorted(glob.glob(os.path.join(samples, "*.docx")))
    if not files:
        fail(f"no sample documents found in {samples}")
    for src in files:
        name = os.path.splitext(os.path.basename(src))[0]
        check_text(name, src, outdir)
        if name == "rtl-layout":
            check_alignment(name, src, outdir)
        elif name == "styles-lists":
            check_styles(name, src, outdir)
        else:
            if pdf_words(name, src, outdir) is not None:
                note(f"{name}: rendered to PDF")
    if failures:
        print(f"{len(failures)} problem(s) found")
        sys.exit(1)
    print(f"all {len(files)} sample documents passed")


if __name__ == "__main__":
    main()
