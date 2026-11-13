"""Serveur Ollama simulé pour les tests PNJ ; écoute uniquement en boucle locale."""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ready", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    args = parser.parse_args()
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            context = json.loads(body["messages"][-1]["content"])
            with lock, args.log.open("a") as log:
                log.write(json.dumps(context) + "\n")
            message = context["message"]
            if message == "PANNE":
                self.send_response(503)
                self.end_headers()
                return
            if message == "LENT":
                time.sleep(3)
            quests = context["available_quests"]
            quest = quests[0]["quest_id"] if quests else ""
            speech = "IA_BONJOUR : je suis Léa, mécanicienne du refuge."
            if message == "INVALIDE":
                quest = "contrat:invente"
            if message == "SOUVENIR":
                speech = "SOUVENIR_ENREGISTRE : je me souviendrai de ton aide."
            if message == "RETOUR":
                remembered = any(turn.get("text") == "SOUVENIR" for turn in context["memory"])
                speech = "MEMOIRE_REPRISE" if remembered else "MEMOIRE_PERDUE"
            envelope = {"message": {"role": "assistant", "content": json.dumps({"speech": speech, "quest_id": quest})}}
            payload = json.dumps(envelope).encode()
            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError):
                pass  # Le client a volontairement interrompu le test de délai.

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    args.ready.write_text(str(server.server_port))
    server.serve_forever()


if __name__ == "__main__":
    main()
