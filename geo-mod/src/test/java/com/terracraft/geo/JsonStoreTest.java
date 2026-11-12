package com.terracraft.geo;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonStoreTest {
    @TempDir Path directory;
    @Test void recoveringThenSavingNeverOverwritesTheGoodBackupWithCorruption() throws Exception {
        Path file=directory.resolve("journal.json"),backup=directory.resolve("journal.json.bak");
        JsonStore.write(file,"{\"version\":1}");JsonStore.write(file,"{\"version\":2}");
        Files.writeString(file,"{corruption");
        assertEquals(1,JsonStore.load(file,JsonParser::parseString).getAsJsonObject().get("version").getAsInt());
        JsonStore.write(file,"{\"version\":3}");
        assertEquals(1,JsonParser.parseString(Files.readString(backup)).getAsJsonObject().get("version").getAsInt());
        assertEquals(3,JsonStore.load(file,JsonParser::parseString).getAsJsonObject().get("version").getAsInt());
    }
    @Test void unreadableDataWithoutBackupRemainsDetectableOnEveryRestart() throws Exception {
        Path file=directory.resolve("journal.json");Files.writeString(file,"{");
        assertNull(JsonStore.load(file,JsonParser::parseString));
        assertTrue(Files.exists(file));
        assertNull(JsonStore.load(file,JsonParser::parseString));
        assertTrue(Files.exists(file));
    }
    @Test void nullDocumentIsNotAcceptedAsAnEmptyNewWorld() throws Exception {
        Path file=directory.resolve("journal.json");Files.writeString(file,"null");
        assertNull(JsonStore.load(file,text -> null));assertTrue(Files.exists(file));
    }
}
