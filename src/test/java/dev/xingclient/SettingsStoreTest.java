package dev.xingclient;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SettingsStoreTest {
    @TempDir Path dir;
    @Test void incompleteSettingsRetainUserChoicesAndFillMissingModules() throws Exception {
        Path file=dir.resolve("settings.json");
        Files.writeString(file,"{\"opacity\":200,\"blossoms\":false,\"modules\":{\"Combat.AutoCrystal\":{\"enabled\":false,\"intensity\":-4,\"mode\":99,\"key\":344}},\"positions\":null}");
        var settings=new SettingsStore(file).load();
        assertEquals(91,settings.modules.size());assertFalse(settings.blossoms);
        assertFalse(settings.modules.get("Combat.AutoCrystal").enabled);
        assertEquals(100,settings.opacity);assertEquals(0,settings.modules.get("Combat.AutoCrystal").intensity);
        assertEquals(2,settings.modules.get("Combat.AutoCrystal").mode);assertEquals(-1,settings.modules.get("Combat.AutoCrystal").key);
        assertNotNull(settings.positions);
    }
    @Test void rapidUpdatesFlushTheLatestSnapshotAndKeepAllPreferences() throws Exception {
        Path file=dir.resolve("nested/settings.json");var store=new SettingsStore(file);var settings=store.load();
        for(int i=40;i<=100;i++){settings.opacity=i;store.save(settings);}
        settings.pink=0x123456;settings.blue=0xabcdef;settings.mascot=false;settings.blossoms=false;
        settings.modules.get("Player.AirPlace").enabled=true;settings.modules.get("Player.AirPlace").mouse=true;settings.modules.get("Player.AirPlace").key=4;
        settings.positions.put("Combat",new ClientSettings.Position(50,70));store.save(settings);store.flush();
        var read=store.load();assertEquals(100,read.opacity);assertEquals(0x123456,read.pink);assertEquals(0xabcdef,read.blue);
        assertFalse(read.mascot);assertFalse(read.blossoms);assertEquals(4,read.modules.get("Player.AirPlace").key);assertTrue(read.modules.get("Player.AirPlace").mouse);
        assertEquals(50,read.positions.get("Combat").x);assertFalse(Files.exists(file.resolveSibling("settings.json.tmp")));
    }
    @Test void malformedFileIsBackedUpBeforeDefaultsAreUsed() throws Exception {
        Path file=dir.resolve("settings.json");Files.writeString(file,"{ broken json");
        var settings=new SettingsStore(file).load();assertEquals(91,settings.modules.size());
        try(var files=Files.list(dir)){assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("settings-invalid-")));}
        assertEquals("{ broken json",Files.readString(file));
    }
    @Test void savedTargetsStayScopedAndMalformedTargetsAreDiscarded() throws Exception {
        var store=new SettingsStore(dir.resolve("targets.json"));var settings=store.load();
        String id="994b7f00-81f8-461d-bd71-181cb07cf9af";
        settings.targets.put("server:one.example",new ClientSettings.SavedTarget(id,"ExamplePlayer"));
        settings.targets.put("server:two.example",new ClientSettings.SavedTarget(id,"OtherPlayer"));
        settings.targets.put("server:broken",new ClientSettings.SavedTarget("bad","Invalid"));
        store.save(settings);store.flush();var restored=store.load();
        assertEquals("ExamplePlayer",restored.targets.get("server:one.example").name);
        assertEquals("OtherPlayer",restored.targets.get("server:two.example").name);
        assertFalse(restored.targets.containsKey("server:broken"));
    }
}
