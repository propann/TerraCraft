package com.terracraft.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;

/** Le modèle choisit un contrat autorisé ; il ne définit jamais ses récompenses. */
final class NpcPolicy {
    record Reply(String speech, String questId) { }

    private NpcPolicy() { }

    static Reply parse(String content, Set<String> allowedQuests) {
        if (content == null || content.length() > 4096) {
            throw new IllegalArgumentException("Réponse PNJ trop longue ou absente");
        }
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();
        if (!json.keySet().equals(Set.of("speech", "quest_id"))
                || !json.get("speech").isJsonPrimitive() || !json.get("speech").getAsJsonPrimitive().isString()
                || !json.get("quest_id").isJsonPrimitive() || !json.get("quest_id").getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Format de réponse PNJ invalide");
        }
        String speech = json.get("speech").getAsString().replaceAll("[\\p{Cntrl}§]", " ").trim();
        String questId = json.get("quest_id").getAsString();
        if (speech.isEmpty() || speech.length() > 500 || (!questId.isEmpty() && !allowedQuests.contains(questId))) {
            throw new IllegalArgumentException("Dialogue ou contrat PNJ non autorisé");
        }
        return new Reply(speech, questId);
    }
}
