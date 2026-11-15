#!/usr/bin/env python3
"""يبني ملف الخريطة العالمية دون اتصال (world.bin) من بيانات Natural Earth (ملكية عامة).

الصيغة (Little-endian):
  int32 عدد الدول
  لكل دولة: int16 عدد الحلقات، ثم لكل حلقة: int32 عدد النقاط، ثم النقاط int16 (lon*32767/180, lat*32767/90)
"""
import json, struct, sys, urllib.request

URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"
out = sys.argv[1] if len(sys.argv) > 1 else "world.bin"
data = json.load(urllib.request.urlopen(URL, timeout=120))
countries = []
for f in data["features"]:
    g = f["geometry"]
    if not g: continue
    polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
    rings = [ring for poly in polys for ring in poly]
    countries.append(rings)
buf = bytearray(struct.pack("<i", len(countries)))
pts = 0
for rings in countries:
    buf += struct.pack("<h", len(rings))
    for ring in rings:
        buf += struct.pack("<i", len(ring))
        for lon, lat, *_ in ring:
            buf += struct.pack("<hh", round(max(-180, min(180, lon)) * 32767 / 180), round(max(-90, min(90, lat)) * 32767 / 90))
            pts += 1
open(out, "wb").write(buf)
print(f"countries={len(countries)} points={pts} bytes={len(buf)}")
