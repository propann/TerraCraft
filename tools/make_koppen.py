#!/usr/bin/env python3
"""Convertit la carte Köppen-Geiger de Beck et al. (2023) en grille brute pour le mod.

Usage : python3 tools/make_koppen.py <koppen_geiger_0p1.tif> geo-mod/src/main/resources/assets/terracraft_geo/koppen_0p1.bin

Source : https://doi.org/10.6084/m9.figshare.21789074 (période 1991-2020, 0,1°), CC BY 4.0.
Sortie : 8 octets d'en-tête (largeur, hauteur en int32 grand-boutiste) puis une classe
(0 = océan, 1-30 = classes Köppen) par case, ligne par ligne du nord (90°N) au sud.
TIFF tuilé compressé en LZW : lu sans dépendance.
"""
import struct
import sys


def lzw_decode(data):
    """Décodeur LZW TIFF (codes de 9 à 12 bits, MSB d'abord, « early change »)."""
    out = bytearray()
    table = [bytes([i]) for i in range(256)] + [b"", b""]
    bits, pos, width, prev = 0, 0, 9, None
    total_bits = len(data) * 8
    while pos + width <= total_bits:
        byte = pos >> 3
        chunk = int.from_bytes(data[byte:byte + 3].ljust(3, b"\0"), "big")
        code = (chunk >> (24 - width - (pos & 7))) & ((1 << width) - 1)
        pos += width
        if code == 256:
            table = table[:258]
            width, prev = 9, None
            continue
        if code == 257:
            break
        if prev is None:
            entry = table[code]
        elif code < len(table):
            entry = table[code]
            table.append(prev + entry[:1])
        else:
            entry = prev + prev[:1]
            table.append(entry)
        out += entry
        prev = entry
        if len(table) + 1 >= (1 << width) and width < 12:
            width += 1
    return bytes(out)


def read_tiff(path):
    d = open(path, "rb").read()
    bo = "<" if d[:2] == b"II" else ">"
    off = struct.unpack(bo + "I", d[4:8])[0]
    count = struct.unpack(bo + "H", d[off:off + 2])[0]
    tags = {}
    for i in range(count):
        tag, typ, cnt, val = struct.unpack(bo + "HHII", d[off + 2 + i * 12:off + 14 + i * 12])
        size = {3: 2, 4: 4}.get(typ, 1)
        if cnt * size > 4 and typ in (3, 4):
            fmt = bo + ("H" if typ == 3 else "I") * cnt
            tags[tag] = list(struct.unpack(fmt, d[val:val + cnt * size]))
        else:
            tags[tag] = [val & 0xFFFF] if typ == 3 and bo == "<" else [val]
    width, height = tags[256][0], tags[257][0]
    tw, th = tags[322][0], tags[323][0]
    assert tags[259][0] == 5, "compression LZW attendue"
    offsets, counts = tags[324], tags[325]
    across = (width + tw - 1) // tw
    grid = bytearray(width * height)
    for t, (o, c) in enumerate(zip(offsets, counts)):
        tile = lzw_decode(d[o:o + c])
        tx, ty = (t % across) * tw, (t // across) * th
        for row in range(th):
            y = ty + row
            if y >= height:
                break
            n = min(tw, width - tx)
            grid[y * width + tx:y * width + tx + n] = tile[row * tw:row * tw + n]
    return width, height, grid


def main():
    width, height, grid = read_tiff(sys.argv[1])
    grid = bytes(v if 1 <= v <= 30 else 0 for v in grid)
    with open(sys.argv[2], "wb") as out:
        out.write(struct.pack(">ii", width, height))
        out.write(grid)
    def at(lat, lon):
        x = int((lon + 180) / 360 * width)
        y = int((90 - lat) / 180 * height)
        return grid[y * width + x]
    names = "- Af Am Aw BWh BWk BSh BSk Csa Csb Csc Cwa Cwb Cwc Cfa Cfb Cfc Dsa Dsb Dsc Dsd Dwa Dwb Dwc Dwd Dfa Dfb Dfc Dfd ET EF".split()
    for place, lat, lon in (("Paris", 48.85, 2.35), ("Sahara", 23, 10), ("Manaus", -3.1, -60), ("Moscou", 55.75, 37.6),
                            ("Groenland", 72, -40), ("Le Caire", 30.04, 31.23), ("Lyon", 45.76, 4.84), ("Atlantique", 35, -40)):
        print(f"  {place:11s} {names[at(lat, lon)]}")
    print(f"{width}×{height} écrit dans {sys.argv[2]} (compresser ensuite avec gzip -9)")


if __name__ == "__main__":
    main()
