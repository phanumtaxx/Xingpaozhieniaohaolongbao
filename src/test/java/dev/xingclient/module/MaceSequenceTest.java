package dev.xingclient.module;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaceSequenceTest {
    private MaceSequence charging() { var s = new MaceSequence(); s.start(); s.charge(64); return s; }
    @Test void requiresAnImpulseForEveryShotAndRespectsInterval() {
        var s = charging(); assertTrue(s.shotDue(2,12)); s.shot();
        for (int i=0;i<12;i++) s.tick();
        assertFalse(s.shotDue(2,12)); assertFalse(s.readyToLaunch(2));
        assertTrue(s.impulse()); assertFalse(s.impulse()); assertTrue(s.shotDue(2,12));
        s.shot(); assertTrue(s.impulse()); assertFalse(s.readyToLaunch(2));
        for (int i=0;i<4;i++) s.tick();
        assertTrue(s.readyToLaunch(2)); assertFalse(s.shotDue(2,12));
    }
    @Test void lateImpulseIsRejectedInsteadOfInventingABoost() {
        var s=charging();s.shot();for(int i=0;i<41;i++)s.tick();
        assertFalse(s.waitingForImpulse());assertFalse(s.impulse());assertFalse(s.readyToLaunch(1));
    }
    @Test void delayedResponseGetsASettlingWindowBeforeLaunch() {
        var s=charging();s.shot();for(int i=0;i<20;i++)s.tick();
        assertTrue(s.impulse());assertFalse(s.readyToLaunch(1));
        for(int i=0;i<4;i++)s.tick();assertTrue(s.readyToLaunch(1));
    }
    @Test void holdRequiresDescentHeightAndGroundWithinReach() {
        var s=charging();s.launch(64);
        s.flight(74,1,10,1.2,4);assertEquals(MaceSequence.Phase.ASCENDING,s.phase());
        s.flight(73,-1,9,1.2,4);assertEquals(MaceSequence.Phase.FALLING,s.phase());
        s.flight(67,-1,3,1.2,4);assertFalse(s.frozen());
        s.flight(65.5,-1,1.5,1.2,4);assertEquals(MaceSequence.Phase.HELD,s.phase());
        assertEquals(8.5,s.fallen());assertTrue(s.message().contains("estimated"));
    }
    @Test void voidGroundContactAndShortFallsDoNotArm() {
        var s=charging();s.launch(64);s.flight(80,1,16,1.2,4);
        s.flight(70,-2,Double.POSITIVE_INFINITY,1.2,4);assertFalse(s.frozen());
        s.flight(64,-2,0,1.2,4);assertFalse(s.frozen());
        s.launch(64);s.flight(66,1,2,1.2,4);s.flight(65,-1,1,1.2,4);assertFalse(s.frozen());
    }
    @Test void heartbeatDoesNotSendEveryTickAndStopAlwaysUnfreezes() {
        var s=charging();int heartbeats=0;
        for(int i=0;i<30;i++){s.tick();if(s.heartbeatDue(10))heartbeats++;}
        assertEquals(3,heartbeats);s.stop("Hit");assertFalse(s.frozen());assertFalse(s.heartbeatDue(10));
        s.start();assertEquals(0,s.shots());assertEquals(0,s.impulses());assertEquals(0,s.fallen());
    }
}
