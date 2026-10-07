#!/usr/bin/env python3
"""Interroge un serveur Minecraft comme la liste des serveurs du jeu (Server List Ping).

Usage : python3 tools/ping_server.py [hôte] [port]
Affiche le MOTD, la version, les joueurs en ligne et si l'icône est présente (JSON).
"""
import json
import socket
import struct
import sys


def varint(value):
    out = b""
    while True:
        byte = value & 0x7F
        value >>= 7
        out += struct.pack("B", byte | (0x80 if value else 0))
        if not value:
            return out


def read_varint(sock):
    result = shift = 0
    while True:
        byte = sock.recv(1)[0]
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result
        shift += 7


def flatten(text):
    """Texte brut d'un composant de chat (chaîne ou objet JSON)."""
    if isinstance(text, str):
        return text
    parts = [text.get("text", "")] + [flatten(extra) for extra in text.get("extra", [])]
    return "".join(parts)


def ping(host="localhost", port=25565):
    with socket.create_connection((host, port), timeout=10) as sock:
        host_bytes = host.encode()
        handshake = varint(0) + varint(767) + varint(len(host_bytes)) + host_bytes \
            + struct.pack(">H", port) + varint(1)
        sock.sendall(varint(len(handshake)) + handshake)
        sock.sendall(varint(1) + varint(0))
        read_varint(sock)
        read_varint(sock)
        length = read_varint(sock)
        data = b""
        while len(data) < length:
            data += sock.recv(length - len(data))
    status = json.loads(data)
    return {
        "motd": flatten(status.get("description", "")),
        "version": status.get("version", {}).get("name"),
        "players": status.get("players", {}).get("online"),
        "icon": status.get("favicon", "").startswith("data:image/png;base64,"),
    }


if __name__ == "__main__":
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25565
    print(json.dumps(ping(host, port), ensure_ascii=False))
