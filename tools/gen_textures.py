#!/usr/bin/env python3
"""Génère les textures TerraCraft (véhicules et objets) en PNG, sans dépendance.

Usage : python3 tools/gen_textures.py   (depuis la racine du projet)
Les textures sont procédurales et déterministes : relancer le script redonne les mêmes fichiers.
"""
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "geo-mod/src/main/resources/assets/terracraft_geo/textures"


def scaled(pixels, factor):
    return [[px for px in row for _ in range(factor)] for row in pixels for _ in range(factor)]


def write_png(path, width, height, pixels):
    """pixels : liste de lignes de tuples RGBA."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


class Canvas:
    def __init__(self, width, height, seed):
        self.w, self.h = width, height
        self.px = [[(0, 0, 0, 0)] * width for _ in range(height)]
        self.rng = random.Random(seed)

    def set(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = color if len(color) == 4 else (*color, 255)

    def rect(self, x, y, w, h, color, noise=0):
        for j in range(y, y + h):
            for i in range(x, x + w):
                if noise:
                    d = self.rng.randint(-noise, noise)
                    c = tuple(max(0, min(255, v + d)) for v in color[:3])
                    self.set(i, j, c)
                else:
                    self.set(i, j, color)

    def rust(self, x, y, w, h, amount):
        for _ in range(int(w * h * amount)):
            i, j = x + self.rng.randrange(w), y + self.rng.randrange(h)
            if 0 <= i < self.w and 0 <= j < self.h and self.px[j][i][3] > 0:
                self.set(i, j, self.rng.choice([(122, 62, 30), (140, 76, 38), (96, 52, 28)]))

    def save(self, name):
        write_png(ROOT / name, self.w, self.h, self.px)


def box(c, u, v, w, h, d, side, top=None, end=None):
    """Patron d'un cube Minecraft : dessus/dessous puis 4 faces latérales."""
    top = top or side
    end = end or side
    c.rect(u + d, v, w, d, top, 10)          # dessus
    c.rect(u + d + w, v, w, d, (40, 40, 40), 6)  # dessous
    c.rect(u, v + d, d, h, side, 10)          # côté droit
    c.rect(u + d, v + d, w, h, end, 10)       # avant
    c.rect(u + d + w, v + d, d, h, side, 10)  # côté gauche
    c.rect(u + 2 * d + w, v + d, w, h, end, 10)  # arrière
    return {"right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
            "top": (u + d, v, w, d)}


def windows(c, face, rows=(2, -2)):
    x, y, w, h = face
    c.rect(x + 2, y + rows[0], w - 4, h + rows[1] - rows[0], (60, 80, 92), 8)
    for _ in range(max(1, w // 6)):  # vitres fêlées
        i = c.rng.randrange(x + 2, x + w - 2)
        for k in range(rows[0], h + rows[1]):
            if c.rng.random() < 0.5:
                c.set(i + c.rng.randint(-1, 1), y + k, (150, 170, 180))


def lights(c, face):
    x, y, w, h = face
    c.rect(x + 2, y + 2, 4, 3, (230, 220, 160))
    c.rect(x + w - 6, y + 2, 4, 3, (230, 220, 160))


def wheel(c, u, v, size):
    faces = box(c, u, v, 4, size, size, (28, 28, 28))
    for name in ("right", "left"):
        x, y, w, h = faces[name]
        c.rect(x + w // 2 - 2, y + h // 2 - 2, 4, 4, (150, 150, 155))


def vehicle(name, size, body, parts, seed):
    c = Canvas(size[0], size[1], seed)
    colour = body
    for part in parts:
        kind, u, v, w, h, d = part
        if kind == "wheel":
            wheel(c, u, v, h)
            continue
        if kind == "turbo":
            box(c, u, v, w, h, d, (170, 170, 175))
            continue
        faces = box(c, u, v, w, h, d, colour)
        if kind == "cabin":
            for face in ("right", "left", "front", "back"):
                windows(c, faces[face])
        if kind == "body":
            lights(c, faces["front"])
            lights(c, faces["back"])
        c.rust(u, v, 2 * (w + d), h + d, 0.12)
    c.save(f"entity/{name}.png")


def icon(name, draw, seed=1):
    c = Canvas(16, 16, seed)
    draw(c)
    c.save(f"item/{name}.png")


def draw_wheel(c):
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if r < 7.5:
                c.set(x, y, (30, 30, 30) if r > 4 else (150, 150, 155) if r > 1.5 else (90, 90, 95))


def draw_engine(c):
    c.rect(2, 5, 12, 9, (90, 92, 98), 12)
    c.rect(4, 2, 2, 3, (60, 60, 64))
    c.rect(10, 2, 2, 3, (60, 60, 64))
    c.rect(1, 8, 1, 3, (180, 40, 30))
    for x in range(3, 14, 3):
        c.rect(x, 6, 1, 7, (60, 62, 66))


def draw_radiator(c):
    c.rect(2, 3, 12, 10, (190, 110, 60), 10)
    for x in range(3, 14, 2):
        c.rect(x, 4, 1, 8, (120, 70, 40))
    c.rect(6, 1, 4, 2, (80, 80, 85))


def draw_battery(c):
    c.rect(2, 4, 12, 10, (30, 30, 34), 6)
    c.rect(3, 2, 3, 2, (200, 40, 40))
    c.rect(10, 2, 3, 2, (40, 80, 200))
    c.rect(4, 8, 8, 2, (230, 200, 40))


def draw_turbo(c):
    for y in range(16):
        for x in range(16):
            r = ((x - 7) ** 2 + (y - 8) ** 2) ** 0.5
            if r < 5.5:
                c.set(x, y, (170, 170, 175) if r > 2 else (80, 80, 85))
    c.rect(11, 3, 4, 3, (150, 150, 155))
    c.rect(1, 11, 4, 3, (150, 150, 155))


def draw_fuel(c):
    c.rect(3, 3, 10, 12, (170, 30, 30), 10)
    c.rect(9, 1, 3, 2, (60, 60, 60))
    c.rect(5, 6, 6, 1, (120, 20, 20))
    c.rect(5, 10, 6, 1, (120, 20, 20))


def draw_chassis(truck):
    def draw(c):
        length = 15 if truck else 12
        c.rect(1, 6, length, 2, (110, 110, 115), 8)
        c.rect(1, 10, length, 2, (110, 110, 115), 8)
        for x in (1, length - 2) + ((7,) if truck else ()):
            c.rect(x, 6, 2, 6, (80, 80, 85))
    return draw


def draw_gun(length, stock):
    def draw(c):
        c.rect(1, 6, length, 3, (85, 85, 94), 6)
        c.rect(1, 6, length, 1, (140, 140, 150))
        c.rect(length - 1, 5, 2, 1, (60, 60, 66))
        c.rect(3, 9, 3, 5, (90, 60, 35) if stock else (40, 40, 45))
        if stock:
            c.rect(0, 7, 3, 4, (90, 60, 35))
    return draw


def draw_smg(c):
    draw_gun(11, False)(c)
    c.rect(6, 9, 2, 5, (60, 60, 66))          # chargeur droit


def draw_sniper(c):
    draw_gun(15, True)(c)
    c.rect(5, 3, 6, 2, (40, 40, 44))          # lunette
    c.rect(5, 3, 1, 2, (90, 140, 200))


def draw_grenade(c):
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 9) ** 2 < 22:
                c.set(x, y, (70, 90, 60) if (x + y) % 3 else (55, 72, 48))
    c.rect(6, 2, 4, 2, (150, 150, 155))
    c.rect(10, 2, 3, 1, (150, 150, 155))


def draw_machete(c):
    for k in range(10):
        c.rect(3 + k, 11 - k, 2, 2, (200, 205, 212))
    c.rect(3, 11, 1, 1, (235, 238, 245))
    c.rect(1, 12, 3, 3, (90, 60, 35))


def draw_ammo(c):
    for x in (3, 7, 11):
        c.rect(x, 5, 3, 8, (200, 160, 60), 8)
        c.rect(x, 3, 3, 2, (150, 90, 50))


def rocket():
    c = Canvas(128, 128, 23)
    hull = box(c, 0, 0, 12, 44, 12, (225, 225, 228), top=(200, 200, 205))
    for name in ("front", "back", "left", "right"):
        x, y, w, h = hull[name]
        for k in range(6, h, 9):
            c.rect(x, y + k, w, 1, (170, 170, 178))
    x, y, w, h = hull["front"]
    for yy in range(8, 14):  # hublot
        for xx in range(3, 9):
            if (xx - 5.5) ** 2 + (yy - 10.5) ** 2 < 8:
                c.set(x + xx, y + yy, (70, 120, 170))
    box(c, 50, 0, 8, 12, 8, (70, 70, 76), top=(50, 50, 54))
    box(c, 50, 24, 8, 8, 8, (190, 40, 35))
    box(c, 50, 44, 4, 4, 4, (190, 40, 35))
    box(c, 0, 60, 2, 14, 6, (190, 40, 35))
    box(c, 0, 84, 13, 10, 13, (200, 120, 60))
    c.rust(0, 0, 48, 56, 0.02)
    c.save("entity/rocket.png")


def draw_hull(c):
    c.rect(5, 1, 6, 14, (225, 225, 228), 6)
    c.rect(6, 4, 4, 3, (70, 120, 170))
    c.rect(5, 10, 6, 1, (170, 170, 178))


def draw_rocket_engine(c):
    c.rect(4, 1, 8, 6, (70, 70, 76), 8)
    c.rect(3, 7, 10, 6, (90, 90, 96), 8)
    c.rect(5, 13, 6, 2, (230, 120, 40))


def draw_rocket_tank(c):
    c.rect(3, 2, 10, 12, (200, 120, 60), 8)
    c.rect(3, 5, 10, 1, (150, 80, 40))
    c.rect(3, 10, 10, 1, (150, 80, 40))
    c.rect(6, 0, 4, 2, (90, 90, 96))


def draw_nose(c):
    for y in range(2, 15):
        half = (y - 1) // 2
        c.rect(8 - half, y, 2 * half, 1, (190, 40, 35))


def draw_fins(c):
    for y in range(3, 14):
        c.rect(2, y, max(1, y - 3), 1, (190, 40, 35))
        c.rect(14 - max(1, y - 3), y, max(1, y - 3), 1, (190, 40, 35))


def draw_rocket_fuel(c):
    c.rect(3, 3, 10, 12, (60, 160, 70), 10)
    c.rect(9, 1, 3, 2, (60, 60, 60))
    c.rect(5, 7, 6, 3, (230, 230, 80))


def draw_helmet(c):
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 8) ** 2) ** 0.5
            if r < 7:
                c.set(x, y, (235, 235, 240))
    c.rect(3, 5, 10, 5, (60, 110, 170), 10)
    c.rect(4, 13, 8, 2, (150, 150, 155))


def draw_oxygen(c):
    c.rect(5, 3, 6, 12, (60, 120, 200), 8)
    c.rect(6, 1, 4, 2, (150, 150, 155))
    c.rect(5, 7, 6, 2, (230, 230, 240))


def block_tex(name, base, pattern, seed=5, alpha=255):
    c = Canvas(16, 16, seed)
    c.rect(0, 0, 16, 16, base, 10)
    pattern(c)
    if alpha < 255:
        for y in range(16):
            for x in range(16):
                r, g, b, a = c.px[y][x]
                if 0 < x < 15 and 0 < y < 15:
                    c.px[y][x] = (r, g, b, alpha)
    c.save(f"block/{name}.png")


def speckles(colours, count, size=2):
    def draw(c):
        for _ in range(count):
            x, y = c.rng.randrange(0, 15), c.rng.randrange(0, 15)
            c.rect(x, y, size, size, c.rng.choice(colours))
    return draw


def frame(colour):
    def draw(c):
        c.rect(0, 0, 16, 1, colour)
        c.rect(0, 15, 16, 1, colour)
        c.rect(0, 0, 1, 16, colour)
        c.rect(15, 0, 1, 16, colour)
    return draw


def hull_pattern(c):
    frame((120, 124, 132))(c)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, (90, 92, 100))


def floor_pattern(c):
    for k in range(0, 16, 3):
        c.rect(k, 0, 1, 16, (70, 72, 78))
        c.rect(0, k, 16, 1, (70, 72, 78))


def window_pattern(c):
    c.rect(1, 1, 14, 14, (120, 170, 210))
    frame((150, 154, 162))(c)
    c.rect(3, 3, 3, 1, (220, 235, 245))


def light_pattern(c):
    c.rect(3, 3, 10, 10, (250, 250, 230), 4)
    frame((150, 154, 162))(c)


def distributor_pattern(c):
    frame((150, 154, 162))(c)
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 16:
                c.set(x, y, (90, 180, 230))
    c.rect(7, 2, 2, 12, (230, 240, 250))


def spider_skin(name, body, accent, eyes):
    c = Canvas(64, 32, 31)
    box(c, 32, 4, 8, 8, 8, body)
    box(c, 0, 0, 6, 6, 6, body)
    box(c, 0, 12, 10, 8, 12, body)
    box(c, 18, 0, 16, 2, 2, accent)
    c.rect(32 + 8 + 1, 4 + 8 + 2, 2, 2, eyes)
    c.rect(32 + 8 + 5, 4 + 8 + 2, 2, 2, eyes)
    c.save(f"entity/{name}.png")


def astronaut_skin(name):
    c = Canvas(64, 64, 37)
    suit = (225, 225, 230)
    head = box(c, 0, 0, 8, 8, 8, suit)
    x, y, w, h = head["front"]
    c.rect(x + 1, y + 2, 6, 4, (200, 150, 40))      # visière dorée
    c.rect(x + 2, y + 3, 2, 1, (250, 220, 120))
    body = box(c, 16, 16, 8, 12, 4, suit)
    x, y, w, h = body["front"]
    c.rect(x + 2, y + 3, 4, 3, (60, 90, 160))         # panneau de commande
    c.rect(x + 3, y + 4, 1, 1, (220, 40, 40))
    for u, v in ((40, 16), (32, 48)):
        arm = box(c, u, v, 4, 12, 4, suit)
        ax, ay, aw, ah = arm["front"]
        c.rect(ax, ay + ah - 3, aw, 3, (120, 140, 120))  # gants abîmés
    for u, v in ((0, 16), (16, 48)):
        leg = box(c, u, v, 4, 12, 4, suit)
        lx, ly, lw, lh = leg["front"]
        c.rect(lx, ly + lh - 3, lw, 3, (110, 110, 118))  # bottes
    c.rust(0, 0, 64, 64, 0.03)
    c.save(f"entity/{name}.png")


def main():
    vehicle("car", (256, 128), (150, 40, 35), [
        ("body", 0, 0, 26, 8, 48), ("cabin", 0, 56, 22, 9, 20),
        ("wheel", 160, 0, 4, 8, 8), ("turbo", 160, 20, 6, 2, 6)], seed=7)
    vehicle("truck", (256, 256), (70, 90, 70), [
        ("body", 0, 0, 32, 8, 72), ("cabin", 0, 80, 30, 19, 20), ("bed", 0, 120, 32, 8, 48),
        ("wheel", 210, 0, 4, 10, 10), ("turbo", 210, 24, 6, 2, 6)], seed=11)
    icon("wheel", draw_wheel)
    icon("engine", draw_engine)
    icon("radiator", draw_radiator)
    icon("battery", draw_battery)
    icon("turbo", draw_turbo)
    icon("fuel_can", draw_fuel)
    icon("car_chassis", draw_chassis(False))
    icon("truck_chassis", draw_chassis(True))
    icon("pistol", draw_gun(9, False))
    icon("rifle", draw_gun(15, True))
    icon("shotgun", draw_gun(13, True))
    icon("ammo", draw_ammo)
    icon("smg", draw_smg)
    icon("sniper", draw_sniper)
    icon("grenade", draw_grenade)
    icon("machete", draw_machete)
    rocket()
    icon("rocket_hull", draw_hull)
    icon("rocket_engine", draw_rocket_engine)
    icon("rocket_tank", draw_rocket_tank)
    icon("nose_cone", draw_nose)
    icon("fins", draw_fins)
    icon("rocket_fuel", draw_rocket_fuel)
    icon("space_helmet", draw_helmet)
    icon("oxygen_tank", draw_oxygen)
    block_tex("titanium_ore", (120, 120, 124), speckles([(200, 205, 215), (170, 180, 195)], 9))
    block_tex("helium3_crystals", (70, 160, 170), speckles([(160, 240, 250), (220, 255, 255), (40, 120, 140)], 18, 3))
    block_tex("station_hull", (175, 178, 186), hull_pattern)
    block_tex("station_floor", (110, 112, 120), floor_pattern)
    block_tex("station_window", (150, 154, 162), window_pattern, alpha=150)
    block_tex("station_light", (150, 154, 162), light_pattern)
    block_tex("oxygen_distributor", (175, 178, 186), distributor_pattern)
    icon("titanium_ingot", lambda c: (c.rect(2, 6, 12, 5, (200, 205, 215), 8), c.rect(2, 6, 12, 1, (235, 238, 245))))
    icon("helium3_shard", lambda c: [c.rect(7 - k // 2, 2 + k, 2 + k, 1, (120, 230, 245)) for k in range(12)])
    spider_skin("moon_crawler", (150, 152, 160), (90, 92, 100), (120, 230, 255))
    astronaut_skin("lost_astronaut")
    print("Textures écrites dans", ROOT)


if __name__ == "__main__":
    main()
