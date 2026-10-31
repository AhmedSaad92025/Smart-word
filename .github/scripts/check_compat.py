#!/usr/bin/env python3
"""Opens engine-produced DOCX samples in LibreOffice and verifies them independently.

usage: check_compat.py <samples-dir> <output-dir>

For every <name>.docx:
  * converts it to UTF-8 text and checks that each line of <name>.expect appears;
  * converts it to PDF (proving the file opens and renders).
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
        else:
            if pdf_words(name, src, outdir) is not None:
                note(f"{name}: rendered to PDF")
    if failures:
        print(f"{len(failures)} problem(s) found")
        sys.exit(1)
    print(f"all {len(files)} sample documents passed")


if __name__ == "__main__":
    main()
