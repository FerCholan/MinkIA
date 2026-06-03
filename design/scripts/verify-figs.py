# -*- coding: utf-8 -*-
import zipfile, re

docx = r"C:\dev\MinkIA\design\docs\MinkIA_Presupuesto_y_Prototipado.docx"
z = zipfile.ZipFile(docx)
xml = z.read("word/document.xml").decode("utf-8")

# placeholders de imagen faltante
print("Placeholders '[falta':", xml.count("[falta"))
print("Placeholders 'no encontrada':", xml.count("no encontrada"))

# numeracion de figuras
figs = re.findall(r"Figura (\d+)\.", xml)
nums = sorted(set(int(n) for n in figs))
print("Figuras detectadas:", len(figs), "| rango:", min(nums), "-", max(nums))
# huecos?
missing = [n for n in range(min(nums), max(nums)+1) if n not in nums]
print("Huecos en numeracion:", missing if missing else "ninguno")

# media
media = [n for n in z.namelist() if n.startswith("word/media/")]
print("Total media:", len(media))
# tamanos para detectar duplicados exactos
sizes = {}
for m in media:
    s = len(z.read(m))
    sizes.setdefault(s, []).append(m)
dups = {s: v for s, v in sizes.items() if len(v) > 1}
print("Grupos con mismo tamano (posible dup):", len(dups))
for s, v in list(dups.items())[:6]:
    print("  ", s, "bytes ->", v)
