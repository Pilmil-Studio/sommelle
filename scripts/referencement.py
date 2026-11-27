#!/usr/bin/env python3
"""Référencement du site (docs/) : adresse canonique, fiche de l'appli pour les moteurs et sitemap.xml.
Le robots.txt, lu seulement à la racine du domaine, est dans le dépôt Pilmil-Studio.github.io.

Usage : python3 scripts/referencement.py URL_DU_SITE NOM_APPLI PAQUET CATEGORIE
Exemple : python3 scripts/referencement.py https://pilmil-studio.github.io/sillon/ Sillon fr.pilmil.sillon LifestyleApplication
Relançable sans risque : chaque ajout est remplacé, jamais dupliqué.
"""
import datetime, html, json, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SKIP_DIRS = {"captures", "marketing", "store", "assets", "img", "fonts", "data"}

def pages():
    for p in sorted(DOCS.rglob("*.html")):
        rel = p.relative_to(DOCS)
        if set(rel.parts[:-1]) & SKIP_DIRS or p.name.startswith(("404", "_")):
            continue
        yield p, rel.as_posix()

def noindex(s):
    return bool(re.search(r'<meta name="robots" content="[^"]*noindex', s))

def url_of(base, rel):
    if rel == "index.html":
        return base
    if rel.endswith("/index.html"):
        return base + rel[: -len("index.html")]
    return base + rel

def lastmod(p):
    try:
        out = subprocess.run(["git", "log", "-1", "--format=%cs", "--", str(p)], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    except OSError:
        out = ""
    return out or datetime.date.today().isoformat()

def main():
    base, app, package, category = sys.argv[1:5]
    if not base.endswith("/"):
        base += "/"
    entries = []
    for p, rel in pages():
        s = p.read_text(encoding="utf-8")
        if noindex(s):
            continue
        url = url_of(base, rel)
        if 'rel="canonical"' not in s:
            s = s.replace("</head>", f'<link rel="canonical" href="{url}">\n</head>', 1)
        if rel in ("index.html", "en/index.html", "fr/index.html"):
            desc = re.search(r'<meta name="description" content="([^"]*)"', s)
            lang = re.search(r'<html[^>]*lang="(\w+)', s)
            ld = {
                "@context": "https://schema.org", "@type": "MobileApplication", "name": app,
                "operatingSystem": "Android", "applicationCategory": category,
                "description": html.unescape(desc.group(1)) if desc else app,
                "url": url, "inLanguage": lang.group(1) if lang else "fr",
                "installUrl": f"https://play.google.com/store/apps/details?id={package}",
                "offers": {"@type": "Offer", "price": "0", "priceCurrency": "EUR"},
                "isAccessibleForFree": True,
                "license": "https://www.mozilla.org/MPL/2.0/",
                "author": {"@type": "Organization", "name": "Pilmil Studio", "url": "https://pilmil-studio.github.io/"},
            }
            block = f'<script type="application/ld+json" data-ref="app">{json.dumps(ld, ensure_ascii=False)}</script>'
            s = re.sub(r'<script type="application/ld\+json" data-ref="app">.*?</script>\n?', "", s, flags=re.S)
            s = s.replace("</head>", block + "\n</head>", 1)
        p.write_text(s, encoding="utf-8")
        entries.append((url, lastmod(p)))
    xml = ['<?xml version="1.0" encoding="UTF-8"?>', '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">']
    xml += [f"  <url><loc>{html.escape(u)}</loc><lastmod>{d}</lastmod></url>" for u, d in entries]
    xml.append("</urlset>")
    (DOCS / "sitemap.xml").write_text("\n".join(xml) + "\n", encoding="utf-8")
    print(f"{len(entries)} pages dans le sitemap")

if __name__ == "__main__":
    main()
