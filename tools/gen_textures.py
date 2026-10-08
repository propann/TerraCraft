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
        if kind == "frame":
            box(c, u, v, w, h, d, (62, 62, 68))  # Cadre, fourche, guidon : métal sombre.
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
    for cy in (11, 23):  # deux hublots cerclés : cabine et soute
        for yy in range(cy - 4, cy + 4):
            for xx in range(1, 11):
                d = (xx - 5.5) ** 2 + (yy - cy + 0.5) ** 2
                if d < 7:
                    c.set(x + xx, y + yy, (60, 110, 165) if (xx, yy) != (4, cy - 2) else (190, 225, 250))
                elif d < 13:
                    c.set(x + xx, y + yy, (120, 124, 132))
    box(c, 50, 0, 8, 12, 8, (70, 70, 76), top=(50, 50, 54))
    box(c, 50, 24, 8, 8, 8, (190, 40, 35))
    box(c, 50, 44, 4, 4, 4, (190, 40, 35))
    box(c, 0, 60, 2, 14, 6, (190, 40, 35))
    box(c, 0, 84, 13, 10, 13, (200, 120, 60))
    booster = box(c, 80, 60, 4, 18, 4, (215, 215, 220), top=(190, 40, 35))  # propulseurs (2 ou 4 réservoirs)
    for name in ("front", "back", "left", "right"):
        bx, by, bw, bh = booster[name]
        c.rect(bx, by + bh - 3, bw, 3, (70, 70, 76))
        c.rect(bx, by + 2, bw, 1, (190, 40, 35))
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


WHITE = (232, 234, 238)
SUIT_ORANGE = (235, 120, 30)
VISOR = (40, 70, 120)


def space_suit_armor():
    """Combinaison spatiale portée (modèle d'armure 64×32) : casque, plastron, bras, bottes, jambières."""
    c = Canvas(64, 32, 41)
    # Casque : coque blanche, grande visière bleutée avec reflet, feux orange sur les côtés.
    head = box(c, 0, 0, 8, 8, 8, WHITE)
    x, y, w, h = head["front"]
    c.rect(x + 1, y + 2, 6, 4, VISOR, 6)
    c.rect(x + 2, y + 2, 2, 1, (150, 200, 240))
    c.rect(x + 1, y + 6, 6, 1, (170, 172, 180))
    for face in ("right", "left"):
        fx, fy, fw, fh = head[face]
        c.rect(fx + 3, fy + 3, 2, 2, SUIT_ORANGE)
    bx, by, bw, bh = head["back"]
    c.rect(bx + 2, by + 1, 4, 6, (190, 192, 200), 6)
    # Plastron : panneau de commande bleu, bandes orange, sac dorsal gris au dos.
    body = box(c, 16, 16, 8, 12, 4, WHITE)
    x, y, w, h = body["front"]
    c.rect(x + 2, y + 2, 4, 3, (60, 90, 160))
    c.rect(x + 3, y + 3, 1, 1, (90, 230, 120))
    c.rect(x + 4, y + 3, 1, 1, (230, 60, 50))
    c.rect(x, y + 7, w, 1, SUIT_ORANGE)
    c.rect(x, y + h - 2, w, 2, (170, 172, 180))
    x, y, w, h = body["back"]
    c.rect(x + 1, y + 1, w - 2, h - 3, (150, 155, 165), 8)
    c.rect(x + 2, y + 2, 2, h - 6, (60, 120, 200))
    c.rect(x + w - 4, y + 2, 2, h - 6, (60, 120, 200))
    # Bras : bande orange à l'épaule, gants gris.
    arm = box(c, 40, 16, 4, 12, 4, WHITE)
    for face in ("right", "front", "left", "back"):
        fx, fy, fw, fh = arm[face]
        c.rect(fx, fy + 1, fw, 1, SUIT_ORANGE)
        c.rect(fx, fy + fh - 3, fw, 3, (120, 124, 132), 6)
    # Jambes de la texture « humanoid » = bottes magnétiques : seul le bas est peint.
    for face, (fx, fy, fw, fh) in {"right": (0, 20, 4, 12), "front": (4, 20, 4, 12),
                                    "left": (8, 20, 4, 12), "back": (12, 20, 4, 12)}.items():
        c.rect(fx, fy + 6, fw, 6, (55, 58, 66), 6)
        c.rect(fx, fy + 6, fw, 1, (240, 200, 40))
        for i in range(fw):
            if i % 2 == 0:
                c.set(fx + i, fy + 7, (30, 30, 30))
            else:
                c.set(fx + i, fy + 7, (240, 200, 40))
    c.rect(4, 16, 4, 4, (55, 58, 66))
    c.rect(8, 16, 4, 4, (40, 42, 48))
    c.save("entity/equipment/humanoid/space_suit.png")

    # Jambières : pantalon blanc, genouillères grises, bande orange.
    c = Canvas(64, 32, 43)
    box(c, 16, 16, 8, 12, 4, WHITE)
    c.rect(16, 16, 24, 4, (0, 0, 0, 0))
    c.rect(20, 20, 8, 12, (0, 0, 0, 0))
    c.rect(16, 20, 4, 12, (0, 0, 0, 0))
    c.rect(28, 20, 12, 12, (0, 0, 0, 0))
    c.rect(20, 26, 8, 6, WHITE, 10)
    c.rect(16, 26, 4, 6, WHITE, 10)
    c.rect(28, 26, 4, 6, WHITE, 10)
    c.rect(32, 26, 8, 6, WHITE, 10)
    c.rect(20, 30, 8, 1, SUIT_ORANGE)
    leg = box(c, 0, 16, 4, 12, 4, WHITE)
    for face in ("right", "front", "left", "back"):
        fx, fy, fw, fh = leg[face]
        c.rect(fx, fy + 4, fw, 2, (150, 154, 162))
        c.rect(fx, fy + 1, fw, 1, SUIT_ORANGE)
    c.save("entity/equipment/humanoid_leggings/space_suit.png")


def draw_space_suit(c):
    c.rect(3, 2, 10, 12, WHITE, 6)
    c.rect(1, 3, 2, 8, WHITE, 6)
    c.rect(13, 3, 2, 8, WHITE, 6)
    c.rect(5, 4, 4, 3, (60, 90, 160))
    c.rect(6, 5, 1, 1, (90, 230, 120))
    c.rect(3, 8, 10, 1, SUIT_ORANGE)
    c.rect(1, 9, 2, 2, (120, 124, 132))
    c.rect(13, 9, 2, 2, (120, 124, 132))
    c.rect(3, 12, 10, 2, (170, 172, 180))


def draw_magnetic_boots(c):
    for x0 in (2, 9):
        c.rect(x0, 4, 5, 8, (55, 58, 66), 6)
        c.rect(x0 - 1, 12, 7, 2, (40, 42, 48))
        for i in range(5):
            c.set(x0 + i, 9, (240, 200, 40) if i % 2 == 0 else (30, 30, 30))
        c.rect(x0, 4, 5, 1, (240, 200, 40))


def jetpack_armor():
    """Jetpack porté : seulement le dos et les côtés du torse (modèle d'armure élargi)."""
    c = Canvas(64, 32, 47)
    # Torse : face arrière en (32, 20) 8×12, côtés en (16, 20) et (28, 20) 4×12.
    for x0 in (32, 37):
        c.rect(x0, 20, 3, 10, (120, 126, 136), 6)          # deux réservoirs
        c.rect(x0, 21, 3, 1, SUIT_ORANGE)
        c.rect(x0, 30, 3, 2, (60, 62, 70))                 # tuyères
        c.set(x0 + 1, 31, (255, 160, 40))
    c.rect(35, 22, 2, 6, (70, 74, 84))                     # bloc central
    c.rect(35, 24, 2, 1, (90, 230, 120))
    for x0 in (16, 28):                                    # sangles sur les côtés
        c.rect(x0 + 1, 20, 2, 12, (70, 74, 84))
    c.save("entity/equipment/humanoid/jetpack.png")


def draw_jetpack(c):
    for x0 in (3, 9):
        c.rect(x0, 2, 4, 10, (120, 126, 136), 6)
        c.rect(x0, 3, 4, 1, SUIT_ORANGE)
        c.rect(x0, 12, 4, 2, (60, 62, 70))
        c.rect(x0 + 1, 14, 2, 1, (255, 160, 40))
    c.rect(7, 4, 2, 6, (70, 74, 84))
    c.set(7, 6, (90, 230, 120))


def plane():
    """Avion léger (modèle 256×128) : fuselage blanc à bande rouge, ailes rayées, cockpit vitré."""
    c = Canvas(256, 128, 53)
    red = (190, 40, 40)
    glass = (120, 170, 200)
    body = box(c, 0, 0, 10, 10, 48, WHITE)
    for face in ("right", "left"):
        x, y, w, h = body[face]
        c.rect(x, y + 4, w, 2, red)
        for i in range(6, w - 6, 8):
            c.rect(x + i, y + 1, 3, 2, glass)
    box(c, 120, 0, 8, 5, 12, glass)
    box(c, 120, 20, 8, 8, 4, red)
    wings = box(c, 0, 60, 72, 2, 14, WHITE)
    x, y, w, h = wings["top"]
    c.rect(x, y, 6, h, red)
    c.rect(x + w - 6, y, 6, h, red)
    box(c, 0, 80, 28, 2, 8, WHITE)
    box(c, 80, 80, 2, 10, 8, red)
    box(c, 180, 0, 24, 2, 1, (60, 50, 40))
    box(c, 180, 10, 2, 2, 1, (150, 150, 155))
    box(c, 200, 20, 2, 4, 4, (35, 35, 38))
    c.rust(0, 0, 256, 128, 0.01)
    c.save("entity/plane.png")


def draw_plane_kit(c):
    c.rect(1, 7, 14, 2, WHITE, 6)
    c.rect(6, 3, 3, 10, WHITE, 6)
    c.rect(6, 3, 3, 2, (190, 40, 40))
    c.rect(4, 12, 7, 1, WHITE)
    c.rect(1, 7, 2, 2, (190, 40, 40))
    c.rect(13, 7, 2, 2, (190, 40, 40))
    c.rect(7, 5, 1, 2, (120, 170, 200))


def beacon_pattern(c):
    """Balise de station : coque grise, anneau lumineux cyan, voyant central."""
    for i in range(16):
        c.set(i, 0, (90, 94, 104))
        c.set(i, 15, (90, 94, 104))
        c.set(0, i, (90, 94, 104))
        c.set(15, i, (90, 94, 104))
    for i in range(3, 13):
        for j in (3, 12):
            c.set(i, j, (80, 220, 255))
            c.set(j, i, (80, 220, 255))
    c.rect(6, 6, 4, 4, (255, 240, 120))
    c.rect(7, 7, 2, 2, (255, 255, 220))


def draw_station_module(c):
    c.rect(2, 4, 12, 9, (175, 178, 186), 8)
    c.rect(2, 4, 12, 1, (120, 124, 132))
    c.rect(4, 7, 2, 2, (120, 190, 230))
    c.rect(10, 7, 2, 2, (120, 190, 230))
    c.rect(7, 9, 2, 4, (60, 62, 70))
    c.rect(7, 2, 2, 2, (255, 240, 120))


def workshop_pattern(c):
    """Atelier de station : établi métallique, bandes orange, outils."""
    c.rect(0, 0, 16, 3, (120, 124, 132))
    for i in range(0, 16, 4):
        c.rect(i, 0, 2, 3, (235, 120, 30))
    c.rect(3, 6, 10, 2, (90, 94, 104))
    c.rect(4, 9, 2, 5, (200, 200, 205))
    c.rect(4, 9, 3, 1, (200, 200, 205))
    c.rect(10, 9, 2, 5, (200, 200, 205))
    c.rect(9, 9, 4, 2, (235, 120, 30))


def server_icon():
    """Icône 64×64 de la liste des serveurs : la Terre et la Lune dans l'espace, une fusée."""
    c = Canvas(64, 64, 61)
    for y in range(64):
        for x in range(64):
            c.set(x, y, (6 + y // 8, 10 + y // 6, 26 + y // 3))
    for _ in range(40):
        x, y = c.rng.randrange(64), c.rng.randrange(64)
        b = c.rng.randint(150, 255)
        c.set(x, y, (b, b, b))
    # La Terre : disque océan, continents, atmosphère.
    cx, cy, r = 26, 38, 21
    for y in range(64):
        for x in range(64):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                land = any(((x - bx) / rx) ** 2 + ((y - by) / ry) ** 2 + 0.25 * ((x * 13 + y * 7) % 5 - 2) / 2 <= 1
                           for bx, by, rx, ry in ((17, 31, 7, 5), (27, 45, 6, 8), (34, 31, 4, 3), (15, 46, 4, 3), (38, 43, 3, 4)))
                shade = max(0.55, 1 - (x - cx + y - cy) / (3.2 * r))
                base = (70, 140, 60) if land else (30, 90, 170)
                c.set(x, y, tuple(int(v * shade) for v in base))
            elif d <= r + 1.6:
                c.set(x, y, (120, 190, 255))
    # La Lune.
    for y in range(64):
        for x in range(64):
            d = ((x - 52) ** 2 + (y - 12) ** 2) ** 0.5
            if d <= 7:
                g = 200 - int(d * 6)
                c.set(x, y, (g, g, g + 6))
    for x, y in ((50, 10), (54, 14), (52, 15)):
        c.set(x, y, (140, 140, 148))
    # Fusée et sa traînée, de la Terre vers la Lune.
    for i in range(9):
        x, y = 30 + i, 30 - i
        c.set(x, y, (255, 170 - i * 10, 40))
        c.set(x + 1, y, (200, 90, 30))
    c.rect(39, 18, 3, 5, (235, 235, 240))
    c.rect(39, 17, 3, 1, (220, 60, 50))
    c.set(40, 16, (220, 60, 50))
    c.set(38, 22, (180, 180, 190))
    c.set(42, 22, (180, 180, 190))
    write_png(ROOT.parent / "server-icon.png", 64, 64, c.px)


def airlock_door():
    """Sas étanche : porte métallique à hublot rond, bandes de sécurité jaunes et noires."""
    for half in ("top", "bottom"):
        c = Canvas(16, 16, 71 if half == "top" else 72)
        c.rect(0, 0, 16, 16, (150, 154, 162), 8)
        c.rect(0, 0, 16, 1, (90, 94, 104))
        c.rect(0, 15, 16, 1, (90, 94, 104))
        c.rect(0, 0, 1, 16, (90, 94, 104))
        c.rect(15, 0, 1, 16, (90, 94, 104))
        if half == "top":
            for y in range(16):
                for x in range(16):
                    d = (x - 7.5) ** 2 + (y - 8) ** 2
                    if d < 14:
                        c.set(x, y, (60, 110, 165))
                    elif d < 22:
                        c.set(x, y, (100, 104, 112))
        else:
            for x in range(1, 15):
                c.set(x, 12, (240, 200, 40) if (x // 2) % 2 == 0 else (30, 30, 30))
                c.set(x, 13, (30, 30, 30) if (x // 2) % 2 == 0 else (240, 200, 40))
            c.rect(12, 5, 2, 3, (70, 74, 84))
        c.save(f"block/airlock_door_{half}.png")
    icon("airlock_door", lambda c: (c.rect(4, 1, 8, 14, (150, 154, 162), 8), c.rect(6, 3, 4, 4, (60, 110, 165)),
                                    c.rect(4, 11, 8, 1, (240, 200, 40))))


def clamp_pattern(c):
    """Pince d'amarrage : plaque orange à rayures, mâchoire grise."""
    for i in range(16):
        for j in range(16):
            if (i + j) % 6 < 3:
                c.set(i, j, (230, 120, 30))
    c.rect(4, 4, 8, 8, (110, 114, 122))
    c.rect(6, 6, 4, 4, (60, 62, 70))


def draw_orbital_station_kit(c):
    c.rect(1, 6, 14, 6, (120, 124, 132), 6)
    c.rect(5, 3, 6, 10, (175, 178, 186), 6)
    c.rect(6, 5, 4, 2, (120, 190, 230))
    c.rect(2, 8, 2, 2, (120, 190, 230))
    c.rect(12, 8, 2, 2, (120, 190, 230))
    c.rect(7, 13, 2, 2, (230, 120, 30))


def rover():
    """Rover lunaire : carrosserie blanche à liseré doré, cabine grise, panneau solaire bleu nuit."""
    c = Canvas(256, 128, 83)
    body = box(c, 0, 0, 24, 6, 40, WHITE)
    for face in ("left", "right", "front", "back"):
        x, y, w, h = body[face]
        c.rect(x, y + h - 2, w, 1, (215, 170, 50))
    lights(c, body["front"])
    box(c, 0, 50, 18, 8, 14, (150, 154, 162))
    panel = box(c, 0, 80, 22, 1, 14, (30, 40, 90))
    x, y, w, h = panel["top"]
    for i in range(0, w, 4):
        c.rect(x + i, y, 1, h, (90, 110, 170))
    for j in range(0, h, 4):
        c.rect(x, y + j, w, 1, (90, 110, 170))
    wheel(c, 160, 0, 9)
    box(c, 160, 30, 4, 2, 4, (170, 170, 175))
    c.save("entity/rover.png")


def draw_rover_kit(c):
    c.rect(1, 4, 14, 10, (150, 120, 70), 6)
    c.rect(1, 4, 14, 1, (110, 85, 50))
    c.rect(3, 7, 10, 4, (225, 225, 228))
    c.rect(3, 11, 2, 2, (40, 40, 44))
    c.rect(11, 11, 2, 2, (40, 40, 44))
    c.rect(5, 6, 6, 1, (30, 40, 90))


def draw_base_kit(colour):
    def draw(c):
        c.rect(1, 9, 14, 5, (120, 124, 132), 6)
        for y in range(3, 10):
            for x in range(2, 14):
                if (x - 7.5) ** 2 / 36 + (y - 9.5) ** 2 / 42 <= 1:
                    c.set(x, y, colour)
        c.rect(6, 6, 4, 2, (120, 190, 230))
        c.rect(7, 12, 2, 2, (230, 120, 30))
    return draw


def alien_patterns():
    """Pierre extraterrestre (violet sombre), glyphes cyan lumineux, cristal lunaire, artefact."""
    def joints(c):
        for k in (0, 8):
            c.rect(0, k, 16, 1, (38, 26, 52))
            c.rect(k + (4 if k else 0), 0, 1, 8, (38, 26, 52))
            c.rect(12 - k, 8, 1, 8, (38, 26, 52))
    block_tex("alien_stone", (70, 52, 92), joints, seed=91)

    def glyph(c):
        c.rect(0, 0, 16, 1, (38, 26, 52))
        c.rect(0, 15, 16, 1, (38, 26, 52))
        cyan = (90, 240, 230)
        for x, y, w, h in [(3, 3, 1, 10), (3, 3, 4, 1), (6, 3, 1, 4), (9, 5, 4, 1), (12, 5, 1, 7),
                           (9, 11, 4, 1), (9, 8, 1, 4), (5, 10, 2, 2)]:
            c.rect(x, y, w, h, cyan)
    block_tex("alien_glyph", (60, 44, 80), glyph, seed=92)

    def crystal(c):
        for x in range(16):
            for y in range(16):
                if (x + y) % 5 == 0 or (x - y) % 7 == 0:
                    c.set(x, y, (210, 250, 255, 255))
    block_tex("lunar_crystal", (120, 200, 235), crystal, seed=93, alpha=200)

    def artifact(c):
        for y in range(2, 14):
            w = 6 - abs(8 - y) // 2
            c.rect(8 - w // 2 - 1, y, w + 2, 1, (70, 52, 92))
        c.rect(6, 5, 4, 1, (90, 240, 230))
        c.rect(7, 6, 2, 4, (90, 240, 230))
        c.rect(6, 10, 4, 1, (90, 240, 230))
    icon("alien_artifact", artifact)


GUN_PALETTE = {
    "k": (24, 24, 28), "m": (58, 60, 68), "M": (92, 95, 105), "h": (160, 165, 176),
    "w": (112, 72, 40), "W": (150, 100, 58), "g": (42, 42, 47), "b": (90, 150, 215), "B": (190, 225, 255),
}


def sprite(rows, palette=GUN_PALETTE):
    """Dessin pixel par pixel : une lettre par pixel (« . » = transparent)."""
    def draw(c):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    c.set(x, y, palette[ch])
    return draw


PISTOL = [
    "................",
    "................",
    "................",
    "................",
    "....kkkkkkkkkkk.",
    "...kMhhhhhhhhhMk",
    "...kmMMMMMMMMMmk",
    "...kmmmmmkkkkkk.",
    "...kmggmk.k.....",
    "...kgggkkk......",
    "..kgggk.........",
    "..kgggk.........",
    ".kgggk..........",
    ".kgggk..........",
    ".kkkkk..........",
    "................",
]
SMG = [
    "................",
    "................",
    "................",
    "................",
    "......kkk.......",
    "kkkkkkkMkkkkkkk.",
    "kMhhhhhhhhhhhhMk",
    "kmMMMMMMMMMMMmkk",
    "kkkmmmmmmmmkk...",
    "..kggkkmmkk.....",
    "..kggk.kmmk.....",
    "..kggk.kmmk.....",
    "..kkkk.kmmk.....",
    ".......kmmk.....",
    ".......kkkk.....",
    "................",
]
RIFLE = [
    "................",
    "................",
    "................",
    "................",
    ".........kk..k..",
    "kkkk..kkkkkkkkkk",
    "kWWWkkMhhhhhhhhk",
    "kWwwwwmMMMWWWkkk",
    ".kwwwwmmmmwwwk..",
    "..kkwwkmmkkkk...",
    "....kwk.kmmk....",
    "....kkk..kmmk...",
    "..........kmmk..",
    "...........kkk..",
    "................",
    "................",
]
SHOTGUN = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "kkkk...kkkkkkkkk",
    "kWWWkkkhhhhhhhhk",
    "kWwwwwmMMMMMMMMk",
    ".kwwwwmmkWWWWWkk",
    "..kkwwkmkWwwwwk.",
    "....kwk.kkkkkkk.",
    "....kkk.........",
    "................",
    "................",
    "................",
    "................",
]
SNIPER = [
    "................",
    "................",
    ".....kkkkkk.....",
    "....kBmmmmbk....",
    ".....kkkkkk.....",
    "kkkk...kk.......",
    "kWWWkkkMMkkkkkkk",
    "kWwwwwMhhhhhhhhk",
    ".kwwwwmMMMMMMMkk",
    "..kkwwkmmkkkk...",
    "....kwk.kmk.....",
    "....kkk.kkk.....",
    "................",
    "................",
    "................",
    "................",
]


def industry_textures():
    """Industrie du carburant : machines, panneau solaire, tuyau, câble, flaque, bidon vide, détecteur."""
    def stripes(c):
        for x in range(0, 16, 4):
            c.rect(x, 12, 2, 4, (25, 25, 25))
        c.rect(0, 12, 16, 1, (25, 25, 25))
        c.rect(3, 2, 10, 7, (70, 70, 76))
        c.rect(5, 4, 6, 3, (35, 35, 38))
    block_tex("oil_pump_side", (215, 170, 40), stripes, seed=101)
    block_tex("oil_pump_top", (70, 70, 76), lambda c: [c.rect(5, 5, 6, 6, (30, 30, 32)), c.rect(6, 6, 4, 4, (15, 15, 15))], seed=102)
    def refinery(c):
        for x in (2, 7, 12):
            c.rect(x, 0, 2, 16, (150, 152, 160))
            c.rect(x, 0, 1, 16, (190, 192, 200))
        c.rect(0, 6, 16, 2, (120, 70, 40))
        c.rect(4, 10, 3, 3, (40, 40, 44))
        c.rect(5, 11, 1, 1, (230, 60, 40))
    block_tex("refinery_side", (95, 98, 108), refinery, seed=103)
    block_tex("refinery_top", (80, 82, 90), lambda c: [c.rect(x, y, 3, 3, (30, 30, 34)) for x in (2, 10) for y in (2, 10)], seed=104)
    def tank(c):
        c.rect(0, 0, 16, 2, (150, 150, 158))
        c.rect(0, 14, 16, 2, (150, 150, 158))
        c.rect(7, 3, 2, 10, (30, 30, 34))
        c.rect(7, 8, 2, 5, (230, 170, 40))
        for x in (2, 13):
            c.rect(x, 2, 1, 12, (200, 200, 208))
    block_tex("fuel_tank_side", (175, 178, 186), tank, seed=105)
    block_tex("fuel_tank_top", (150, 152, 160), lambda c: [c.rect(5, 5, 6, 6, (110, 112, 120)), c.rect(7, 7, 2, 2, (40, 40, 44))], seed=106)
    def pump(c):
        c.rect(3, 2, 10, 5, (235, 235, 225))
        c.rect(4, 3, 3, 1, (40, 40, 40))
        c.rect(9, 3, 3, 1, (40, 40, 40))
        c.rect(4, 5, 8, 1, (40, 40, 40))
        c.rect(12, 8, 3, 6, (30, 30, 32))
        c.rect(3, 9, 6, 1, (255, 210, 60))
    block_tex("fuel_pump_side", (185, 35, 30), pump, seed=107)
    block_tex("fuel_pump_top", (90, 92, 100), lambda c: c.rect(2, 2, 12, 12, (185, 35, 30)), seed=108)
    def panel(c):
        c.rect(0, 0, 16, 16, (190, 195, 205))
        c.rect(1, 1, 14, 14, (25, 40, 95))
        for k in range(1, 15, 7):
            c.rect(k, 1, 1, 14, (110, 130, 190))
            c.rect(1, k, 14, 1, (110, 130, 190))
        c.rect(3, 3, 2, 1, (170, 190, 240))
    block_tex("solar_panel_top", (25, 40, 95), panel, seed=109)
    def battery(c):
        c.rect(1, 1, 14, 14, (40, 42, 48))
        c.rect(3, 3, 10, 10, (25, 25, 28))
        for k, colour in enumerate([(80, 220, 110)] * 3 + [(60, 70, 64)] * 2):
            c.rect(4, 11 - 2 * k, 8, 1, colour)
        c.rect(6, 0, 4, 1, (190, 110, 60))
    block_tex("battery_bank_side", (60, 62, 70), battery, seed=114)
    def generator(c):
        c.rect(1, 3, 14, 10, (60, 120, 60))
        for y in range(4, 12, 2):
            c.rect(2, y, 6, 1, (40, 80, 40))
        c.rect(9, 5, 5, 5, (35, 35, 38))
        c.rect(10, 6, 3, 3, (200, 200, 205))
        c.rect(0, 13, 16, 3, (45, 45, 50))
    block_tex("generator_side", (70, 72, 78), generator, seed=116)
    def greenhouse(c):
        c.rect(0, 0, 16, 16, (190, 225, 230))
        for x in (0, 7, 15):
            c.rect(x, 0, 1, 16, (150, 152, 160))
        c.rect(0, 0, 16, 1, (150, 152, 160))
        c.rect(1, 11, 14, 4, (90, 60, 35))
        for x in (2, 5, 9, 12):
            c.rect(x, 6, 1, 5, (70, 160, 60))
            c.rect(x - 1, 7, 3, 1, (100, 190, 80))
    block_tex("greenhouse_side", (190, 225, 230), greenhouse, seed=122)
    block_tex("greenhouse_top", (190, 225, 230), lambda c: [c.rect(x, 0, 1, 16, (150, 152, 160)) for x in (0, 7, 15)], seed=123)
    def lamp(glow):
        def draw(c):
            c.rect(0, 0, 16, 16, (70, 72, 80))
            c.rect(2, 2, 12, 12, glow)
            for k in (5, 10):
                c.rect(k, 2, 1, 12, (70, 72, 80))
                c.rect(2, k, 12, 1, (70, 72, 80))
        return draw
    block_tex("electric_lamp_off", (70, 72, 80), lamp((110, 110, 100)), seed=118)
    def terminal(c):
        c.rect(2, 2, 12, 8, (20, 30, 40))
        c.rect(3, 3, 10, 6, (40, 120, 140))
        for y in (4, 6):
            c.rect(4, y, 7, 1, (140, 230, 240))
        c.rect(3, 11, 10, 3, (90, 92, 100))
        for x in (4, 7, 10):
            c.rect(x, 12, 2, 1, (215, 170, 40))
    block_tex("logistics_terminal_side", (120, 124, 132), terminal, seed=120)
    block_tex("logistics_terminal_top", (120, 124, 132), lambda c: c.rect(4, 4, 8, 8, (215, 170, 40)), seed=121)
    block_tex("electric_lamp_on", (70, 72, 80), lamp((255, 240, 190)), seed=119)
    block_tex("generator_top", (60, 120, 60), lambda c: [c.rect(5, 5, 3, 3, (30, 30, 30)), c.rect(10, 9, 3, 3, (190, 40, 30))], seed=117)
    block_tex("battery_bank_top", (60, 62, 70), lambda c: [c.rect(3, 3, 3, 3, (190, 110, 60)), c.rect(10, 3, 3, 3, (30, 30, 34))], seed=115)
    block_tex("solar_panel_side", (175, 180, 190), lambda c: c.rect(0, 0, 16, 2, (120, 125, 135)), seed=110)
    block_tex("pipe", (135, 138, 148), lambda c: [c.rect(0, 0, 16, 1, (175, 178, 188)), c.rect(0, 7, 16, 2, (95, 98, 108)),
                                                 c.rect(0, 15, 16, 1, (90, 92, 100))], seed=111)
    block_tex("cable", (28, 28, 30), lambda c: [c.rect(0, 7, 16, 2, (190, 110, 60))], seed=112)
    def puddle(c):
        for x in range(16):
            for y in range(16):
                if (x * 7 + y * 3) % 11 == 0:
                    c.set(x, y, (60, 40, 90))
                elif (x + y * 5) % 13 == 0:
                    c.set(x, y, (40, 70, 80))
    block_tex("oil_puddle", (12, 10, 14), puddle, seed=113)

    def can(fill):
        def draw(c):
            c.rect(4, 3, 8, 12, (24, 24, 28))
            c.rect(5, 4, 6, 10, fill)
            c.rect(5, 4, 6, 1, (200, 200, 205))
            c.rect(9, 1, 3, 3, (24, 24, 28))
            c.rect(10, 2, 1, 1, (150, 150, 155))
            c.rect(6, 7, 4, 4, (24, 24, 28))
            c.rect(7, 8, 2, 2, fill)
        return draw
    icon("empty_fuel_can", can((120, 122, 130)))

    def detector(c):
        c.rect(3, 1, 10, 14, (24, 24, 28))
        c.rect(4, 2, 8, 12, (215, 170, 40))
        c.rect(5, 3, 6, 5, (20, 40, 25))
        c.rect(6, 5, 4, 1, (90, 230, 120))
        c.rect(7, 4, 1, 3, (90, 230, 120))
        c.rect(6, 10, 2, 2, (24, 24, 28))
        c.rect(9, 10, 2, 2, (180, 40, 30))
    icon("oil_detector", detector)


def main():
    vehicle("car", (256, 128), (150, 40, 35), [
        ("body", 0, 0, 26, 8, 48), ("cabin", 0, 56, 22, 9, 20),
        ("wheel", 160, 0, 4, 8, 8), ("turbo", 160, 20, 6, 2, 6)], seed=7)
    vehicle("truck", (256, 256), (70, 90, 70), [
        ("body", 0, 0, 32, 8, 72), ("cabin", 0, 80, 30, 19, 20), ("bed", 0, 120, 32, 8, 48),
        ("wheel", 210, 0, 4, 10, 10), ("turbo", 210, 24, 6, 2, 6)], seed=11)
    vehicle("motorcycle", (128, 64), (150, 35, 30), [
        ("body", 0, 0, 6, 4, 28), ("tank", 0, 32, 8, 4, 14), ("frame", 100, 0, 2, 12, 2),
        ("frame", 70, 24, 14, 1, 1), ("wheel", 70, 0, 4, 10, 10), ("turbo", 70, 40, 2, 2, 8)], seed=13)
    icon("wheel", draw_wheel)
    icon("engine", draw_engine)
    icon("radiator", draw_radiator)
    icon("battery", draw_battery)
    icon("turbo", draw_turbo)
    icon("fuel_can", draw_fuel)
    icon("car_chassis", draw_chassis(False))
    icon("truck_chassis", draw_chassis(True))
    icon("pistol", sprite(PISTOL))
    icon("rifle", sprite(RIFLE))
    icon("shotgun", sprite(SHOTGUN))
    icon("ammo", draw_ammo)
    icon("smg", sprite(SMG))
    icon("sniper", sprite(SNIPER))
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
    icon("space_suit", draw_space_suit)
    icon("magnetic_boots", draw_magnetic_boots)
    space_suit_armor()
    icon("jetpack", draw_jetpack)
    jetpack_armor()
    plane()
    icon("plane_kit", draw_plane_kit)
    block_tex("titanium_ore", (120, 120, 124), speckles([(200, 205, 215), (170, 180, 195)], 9))
    block_tex("helium3_crystals", (70, 160, 170), speckles([(160, 240, 250), (220, 255, 255), (40, 120, 140)], 18, 3))
    block_tex("station_hull", (175, 178, 186), hull_pattern)
    block_tex("station_floor", (110, 112, 120), floor_pattern)
    block_tex("station_window", (150, 154, 162), window_pattern, alpha=150)
    block_tex("station_light", (150, 154, 162), light_pattern)
    block_tex("oxygen_distributor", (175, 178, 186), distributor_pattern)
    block_tex("station_beacon", (150, 154, 162), beacon_pattern)
    block_tex("station_workshop", (150, 154, 162), workshop_pattern)
    server_icon()
    airlock_door()
    block_tex("docking_clamp", (150, 154, 162), clamp_pattern)
    icon("orbital_station_kit", draw_orbital_station_kit)
    rover()
    icon("rover_kit", draw_rover_kit)
    alien_patterns()
    industry_textures()
    block_tex("market_stall_top", (200, 40, 40), lambda c: [c.rect(x, 0, 2, 16, (235, 230, 220)) for x in range(0, 16, 4)], seed=95)
    block_tex("market_stall_side", (150, 105, 60), lambda c: [c.rect(0, 0, 16, 4, (200, 40, 40)), c.rect(0, 4, 16, 1, (90, 60, 35)),
                                                              c.rect(0, 9, 16, 1, (110, 75, 45)), c.rect(0, 15, 16, 1, (90, 60, 35))]
              + [c.rect(x, 0, 2, 4, (235, 230, 220)) for x in range(0, 16, 4)], seed=96)
    block_tex("market_stall_bottom", (140, 100, 58), lambda c: [c.rect(0, y, 16, 1, (105, 72, 42)) for y in range(3, 16, 4)], seed=97)
    icon("commander_badge", lambda c: [c.rect(5, 1, 6, 3, (170, 30, 30)), c.rect(6, 4, 4, 2, (200, 200, 205)),
                                       c.rect(3, 6, 10, 8, (215, 170, 50)), c.rect(5, 8, 6, 4, (170, 120, 30)),
                                       c.rect(7, 7, 2, 6, (250, 220, 120)), c.rect(6, 9, 4, 2, (250, 220, 120))])
    icon("lunar_base_kit", draw_base_kit((200, 200, 206)))
    icon("mars_base_kit", draw_base_kit((200, 110, 70)))
    icon("station_module", draw_station_module)
    icon("titanium_ingot", lambda c: (c.rect(2, 6, 12, 5, (200, 205, 215), 8), c.rect(2, 6, 12, 1, (235, 238, 245))))
    icon("helium3_shard", lambda c: [c.rect(7 - k // 2, 2 + k, 2 + k, 1, (120, 230, 245)) for k in range(12)])
    spider_skin("moon_crawler", (150, 152, 160), (90, 92, 100), (120, 230, 255))
    astronaut_skin("lost_astronaut")
    print("Textures écrites dans", ROOT)


if __name__ == "__main__":
    main()
