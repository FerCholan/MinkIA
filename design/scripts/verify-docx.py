# -*- coding: utf-8 -*-
import zipfile, sys

docx = r"C:\dev\MinkIA\design\docs\MinkIA_Presupuesto_y_Prototipado.docx"
z = zipfile.ZipFile(docx)

media = [n for n in z.namelist() if n.startswith("word/media/")]
print("IMAGENES embebidas:", len(media))

xml = z.read("word/document.xml").decode("utf-8")

def chk_present(label, *needles):
    for n in needles:
        ok = n in xml
        print(("  [OK] " if ok else "  [FALTA] ") + "%-14s %r" % (label, n))

def chk_absent(label, *needles):
    for n in needles:
        ok = n not in xml
        print(("  [OK] " if ok else "  [TODAVIA ESTA] ") + "%-14s %r" % (label, n))

print("\n--- DEBE ESTAR ---")
chk_present("simbolo", "hoja-circuito")
chk_present("4.1.3", "Logotipo y s")
chk_present("presup", "95.00")
chk_present("subtitulo", "C01.", "A01.")
chk_present("caption gen", "Wireframe (izquierda)")
chk_present("paleta new", "1B4228", "69802D", "BD4C18")
chk_present("constr", "rea de protecci")

print("\n--- NO DEBE ESTAR ---")
chk_absent("cuadro2", "Cuadro 2", "Escenarios de inversi")
chk_absent("total viejo", "950.00", "170.00", "700.00", "135.00")
chk_absent("hardware", "Hardware")
chk_absent("dominio", "dominio")
chk_absent("opcional", "opcional")
chk_absent("aclarar", "Es necesario aclarar")
chk_absent("paleta old", "1E4D2B", "6FA84F", "D14A22")
chk_absent("logo viejo", "logo_appMov")
