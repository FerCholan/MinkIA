# -*- coding: utf-8 -*-
import io, sys

REPL = [
    ("#143A20", "#0f2616"),
    ("#1E4D2B", "#1b4228"),
    ("#256536", "#1f4a2c"),
    ("#3E8E4F", "#5e8a2f"),
    ("#6FA84F", "#69802d"),
    ("#8FBF6E", "#8aa54a"),
    ("#D14A22", "#bd4c18"),
    ("#C8451E", "#a23f10"),
    ("#F4855E", "#df7a48"),
    ("#B3271A", "#8f2810"),
    ("209,74,34", "189,76,24"),
    ("62,142,79", "94,138,47"),
    ("30,77,43", "27,66,40"),
]

path = sys.argv[1]
with io.open(path, "r", encoding="utf-8") as f:
    txt = f.read()

total = 0
for old, new in REPL:
    n = txt.count(old)
    total += n
    txt = txt.replace(old, new)
    print("%-12s -> %-10s : %d" % (old, new, n))

with io.open(path, "w", encoding="utf-8") as f:
    f.write(txt)

print("TOTAL replacements:", total)
