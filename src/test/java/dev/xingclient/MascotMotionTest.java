package dev.xingclient;

import dev.xingclient.ui.MascotMotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MascotMotionTest {
    private MascotMotion lifted(){
        var m=new MascotMotion();m.layout(1440,900);
        m.grab(m.x()+50,m.y()+50);m.drag(650,100);return m;
    }
    @Test void followsGrabOffsetAndOnlyFallsAfterRelease(){
        var m=lifted();assertEquals(600,m.x());assertEquals(50,m.y());
        m.advance(2);assertEquals(50,m.y());
        m.release();m.advance(.5);assertEquals(90,m.y(),.001);
        m.advance(1.5);assertFalse(m.airborne());assertEquals(900-m.height(),m.y(),.001);
        m.advance(10);assertEquals(900-m.height(),m.y(),.001);
    }
    @Test void fallDistanceIsIndependentOfFrameRate(){
        var low=lifted();var high=lifted();low.release();high.release();
        for(int i=0;i<45;i++)low.advance(1.0/30);
        for(int i=0;i<216;i++)high.advance(1.0/144);
        assertEquals(low.y(),high.y(),.00001);assertTrue(low.airborne());
    }
    @Test void regrabbingStopsFallAndResizeKeepsHerReachable(){
        var m=lifted();m.release();m.advance(.8);
        m.grab(m.x()+20,m.y()+30);double heldY=m.y();m.advance(2);assertEquals(heldY,m.y());
        m.drag(-100,-100);assertEquals(0,m.x());assertEquals(0,m.y());
        m.drag(5000,5000);assertEquals(1440-m.width(),m.x(),.001);assertEquals(900-m.height(),m.y(),.001);
        m.layout(1000,600);assertFalse(m.held());assertEquals(1000-m.width(),m.x(),.001);assertEquals(600-m.height(),m.y(),.001);
    }
}
