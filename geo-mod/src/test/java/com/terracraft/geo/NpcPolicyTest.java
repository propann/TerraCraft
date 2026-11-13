package com.terracraft.geo;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NpcPolicyTest {
    private final Set<String> allowed = Set.of("contrat:commerce");

    @Test void acceptsOnlyAvailableQuest() {
        var reply = NpcPolicy.parse("{\"speech\":\"Bonjour !\",\"quest_id\":\"contrat:commerce\"}", allowed);
        assertEquals("contrat:commerce", reply.questId());
    }

    @Test void acceptsDialogueWithoutQuest() {
        assertEquals("", NpcPolicy.parse("{\"speech\":\"Reviens demain.\",\"quest_id\":\"\"}", Set.of()).questId());
    }

    @Test void rejectsInventedQuest() {
        assertThrows(IllegalArgumentException.class, () -> NpcPolicy.parse(
                "{\"speech\":\"Un million de crédits.\",\"quest_id\":\"contrat:invente\"}", allowed));
    }

    @Test void rejectsRewardOrCommandFields() {
        assertThrows(IllegalArgumentException.class, () -> NpcPolicy.parse(
                "{\"speech\":\"Bonjour\",\"quest_id\":\"\",\"reward\":1000000}", allowed));
        assertThrows(IllegalArgumentException.class, () -> NpcPolicy.parse(
                "{\"speech\":\"Bonjour\",\"quest_id\":\"\",\"command\":\"op Alice\"}", allowed));
    }

    @Test void rejectsWrongTypesAndMissingFields() {
        for (String json : new String[]{"{}", "{\"speech\":1,\"quest_id\":\"\"}",
                "{\"speech\":\"Bonjour\",\"quest_id\":null}"}) {
            assertThrows(RuntimeException.class, () -> NpcPolicy.parse(json, allowed));
        }
    }

    @Test void boundsDialogueAndEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> NpcPolicy.parse(
                "{\"speech\":\"" + "x".repeat(501) + "\",\"quest_id\":\"\"}", allowed));
        assertThrows(IllegalArgumentException.class, () -> NpcPolicy.parse("x".repeat(4097), allowed));
    }

    @Test void removesFormattingAndControlCharacters() {
        assertEquals("Bonjour  a", NpcPolicy.parse(
                "{\"speech\":\"Bonjour\\n§a\",\"quest_id\":\"\"}", allowed).speech());
    }
}
