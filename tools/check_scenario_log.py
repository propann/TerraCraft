"""Contrôle des exceptions du scénario ; seules les pannes externes identifiées sont tolérées."""

from pathlib import Path
import re
import sys
import uuid


HEADER = re.compile(r"^\[\d{2}:\d{2}:\d{2}\] \[[^\]]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]: ")
PROFILE_NAMES = {"alice", "bob", "eve", "dan"}


def external_outage(event):
    """Reconnaît uniquement les erreurs de dépendances observées dans le banc."""
    header = event.splitlines()[0]
    exceptions = [line for line in event.splitlines()
                  if "Exception" in line and not line.lstrip().startswith("at ")]
    if (len(event.splitlines()) == 1 and re.search(
            r"/WARN\]: Météo Open-Meteo indisponible \(java\.util\.concurrent\.CompletionException: "
            r"java\.net\.http\.(?:HttpConnectTimeoutException: HTTP connect timed out|"
            r"HttpTimeoutException: request timed out)\)$", header)):
        return True
    if exceptions != ["MinecraftClientHttpException[type=HTTP_ERROR, status=429, response=null]"]:
        return False
    if "com.mojang.authlib.minecraft.client.MinecraftClient." not in event:
        return False
    name = re.search(r"/WARN\]: Couldn't find profile with name: ([A-Za-z]+)$", header)
    profile = re.search(r"/WARN\]: Couldn't look up profile properties for ([0-9a-f-]+)$", header)
    if name and name.group(1).lower() in PROFILE_NAMES:
        return True
    if profile and "com.mojang.authlib.services.MinecraftServicesSessionService.fetchProfile" in event:
        try:
            uuid.UUID(profile.group(1))
            return True
        except ValueError:
            pass
    return False


def classify_exceptions(log):
    events = []
    current = []
    for line in log.splitlines():
        if HEADER.match(line) and current:
            events.append("\n".join(current))
            current = []
        current.append(line)
    if current:
        events.append("\n".join(current))
    unexpected, external = [], []
    for event in events:
        if "Exception" not in event:
            continue
        (external if external_outage(event) else unexpected).append(event)
    return unexpected, external


def check_log(log):
    unexpected, external = classify_exceptions(log)
    for event in external:
        print("  INFO  dépendance externe indisponible : " + event.splitlines()[0])
    for event in unexpected:
        print("  ÉCHEC exception inattendue :\n" + event)
    return not unexpected


if __name__ == "__main__":
    sys.exit(0 if check_log(Path(sys.argv[1]).read_text(errors="replace")) else 1)
