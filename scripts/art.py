#!/usr/bin/env python3
"""Renders the README banner and the website hero from one drawing.

    python3 scripts/art.py            # needs Google Chrome and cwebp

Writes docs/banner.webp (1600 x 520) and docs/hero.png (2912 x 1440, the product-page kit's --hero).
The mark comes from scripts/icon.py's docs/mark.svg, so the icon is never drawn twice.
"""
import math, pathlib, subprocess, tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BG = "#0D100B"
MARK = (ROOT / "docs/mark.svg").read_text()

def curve(i, n):
    """A response curve with a bass shelf, a dip and a presence peak — 0..1 for band i of n."""
    x = i / (n - 1)
    v = 0.46 + 0.30 * math.exp(-((x - 0.10) / 0.16) ** 2) - 0.20 * math.exp(-((x - 0.42) / 0.14) ** 2) \
        + 0.40 * math.exp(-((x - 0.76) / 0.13) ** 2)
    return max(0.12, min(0.97, v))

def meter(x, y, w, h, n, gap_ratio=0.5):
    """n level bars in a box; the two loudest carry an amber peak-hold cap."""
    bw = w / (n + (n - 1) * gap_ratio); gap = bw * gap_ratio
    vals = [curve(i, n) for i in range(n)]
    top2 = sorted(range(n), key=lambda i: -vals[i])[:1] + [min(range(n // 3), key=lambda i: -vals[i])]
    out = []
    for i, v in enumerate(vals):
        bx = x + i * (bw + gap); bh = h * v
        out.append(f'<rect x="{bx:.1f}" y="{y:.1f}" width="{bw:.1f}" height="{h:.1f}" rx="{bw/2:.1f}" fill="#fff" opacity=".045"/>')
        out.append(f'<rect x="{bx:.1f}" y="{y+h-bh:.1f}" width="{bw:.1f}" height="{bh:.1f}" rx="{bw/2:.1f}" fill="url(#bar)"/>')
        cap_y = y + h - bh - bw * 0.95
        col = "url(#cap)" if i in top2 else "#A0D57B"
        op = "1" if i in top2 else ".38"
        out.append(f'<rect x="{bx:.1f}" y="{cap_y:.1f}" width="{bw:.1f}" height="{bw*0.42:.1f}" rx="{bw*0.21:.1f}" fill="{col}" opacity="{op}"/>')
    return "".join(out)

DEFS = '''<defs>
<linearGradient id="bar" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#C9F7A6"/><stop offset="1" stop-color="#6FAE48"/></linearGradient>
<linearGradient id="cap" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FFD067"/><stop offset="1" stop-color="#F2B233"/></linearGradient>
<radialGradient id="glow"><stop offset="0" stop-color="#A0D57B" stop-opacity=".30"/><stop offset=".55" stop-color="#A0D57B" stop-opacity=".08"/><stop offset="1" stop-color="#A0D57B" stop-opacity="0"/></radialGradient>
<radialGradient id="amber"><stop offset="0" stop-color="#F5BF48" stop-opacity=".16"/><stop offset="1" stop-color="#F5BF48" stop-opacity="0"/></radialGradient>
</defs>'''
FONT = "font-family:'SF Pro Display','Helvetica Neue',Helvetica,Arial,sans-serif"

def mark(x, y, size):
    inner = MARK.split(">", 1)[1].rsplit("</svg>", 1)[0].replace('id="', 'id="m-').replace("url(#", "url(#m-")
    return f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="18 18 72 72">{inner}</svg>'

def banner():
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="520" viewBox="0 0 1600 520">{DEFS}
<rect width="1600" height="520" fill="{BG}"/>
<ellipse cx="1190" cy="330" rx="620" ry="380" fill="url(#glow)"/>
{mark(88, 122, 168)}
<text x="296" y="232" style="{FONT};font-weight:700;font-size:118px;letter-spacing:-3px" fill="#E8EBDF">Pegel</text>
<text x="300" y="290" style="{FONT};font-weight:400;font-size:38px" fill="#B9C2AE">Shape what you hear.</text>
<text x="90" y="392" style="{FONT};font-weight:400;font-size:26px;letter-spacing:.3px" fill="#8D9387">Equalizer · Bass · Loudness · Virtualizer · Profiles</text>
{meter(850, 70, 680, 380, 20)}
</svg>'''

def hero():
    W, H = 2912, 1440
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">{DEFS}
<rect width="{W}" height="{H}" fill="{BG}"/>
<ellipse cx="800" cy="800" rx="1150" ry="800" fill="url(#glow)"/>
<ellipse cx="1150" cy="330" rx="520" ry="360" fill="url(#amber)"/>
{meter(170, 210, 1280, 1010, 16, 0.46)}
{mark(1740, 930, 250)}
<text x="2040" y="1090" style="{FONT};font-weight:700;font-size:232px;letter-spacing:-6px" fill="#EEF1E6">Pegel</text>
<text x="2046" y="1190" style="{FONT};font-weight:400;font-size:70px" fill="#B9C2AE">Shape what you hear.</text>
</svg>'''

def render(svg, w, h, out_png):
    with tempfile.TemporaryDirectory() as d:
        page = pathlib.Path(d) / "a.html"
        page.write_text(f'<body style="margin:0;background:{BG}">{svg}</body>')
        subprocess.run([CHROME, "--headless", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        f"--window-size={w},{h}", f"--screenshot={out_png}", f"file://{page}"],
                       check=True, capture_output=True)

if __name__ == "__main__":
    tmp = ROOT / "docs/_banner.png"
    render(banner(), 1600, 520, tmp)
    subprocess.run(["cwebp", "-quiet", "-q", "90", str(tmp), "-o", str(ROOT / "docs/banner.webp")], check=True)
    tmp.unlink()
    render(hero(), 2912, 1440, ROOT / "docs/hero.png")
    print("docs/banner.webp, docs/hero.png")
