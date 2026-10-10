package dev.xingclient;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaceSettingsTest {
    @TempDir Path dir;
    @Test void boundsAndFiniteChecksApplyToCopiedSettings() {
        var s=new ClientSettings.MaceKillSettings();
        s.charges=100;s.heartbeatTicks=0;s.chargeIntervalTicks=-1;s.holdSeconds=Integer.MAX_VALUE;
        s.holdHeight=Double.NaN;s.minimumFall=Double.NEGATIVE_INFINITY;s.attackRange=100;
        var copy=s.copy();assertEquals(8,copy.charges);assertEquals(1,copy.heartbeatTicks);
        assertEquals(10,copy.chargeIntervalTicks);assertEquals(30,copy.holdSeconds);
        assertEquals(1.2,copy.holdHeight);assertEquals(4,copy.minimumFall);assertEquals(3,copy.attackRange);
        s.charges=1;assertEquals(8,copy.charges);assertFalse(copy.autoAttack);
    }
    @Test void optionsRoundTripAndNullOldSettingsAcquireDefaults() {
        var store=new SettingsStore(dir.resolve("settings.json"));var s=new ClientSettings();
        s.maceKill.charges=4;s.maceKill.autoAttack=true;s.maceKill.holdHeight=.8;
        store.save(s);store.flush();var loaded=store.load();
        assertEquals(4,loaded.maceKill.charges);assertTrue(loaded.maceKill.autoAttack);assertEquals(.8,loaded.maceKill.holdHeight);
        loaded.maceKill=null;assertEquals(5,loaded.normalize().maceKill.charges);
    }
}
