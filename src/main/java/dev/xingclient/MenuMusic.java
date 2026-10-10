package dev.xingclient;

import java.io.BufferedInputStream;
import javax.sound.sampled.*;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.Player;
import org.slf4j.LoggerFactory;

/** One streaming player survives screen changes. PCM is played at unity gain, outside the game mixer. */
public final class MenuMusic implements AutoCloseable {
    private static final boolean MUSIC_ENABLED = false;
    private final Object gate=new Object();
    private final boolean silentCheck;
    private volatile boolean active,closed;
    private volatile long frames;
    private volatile Throwable failure;
    private volatile SourceDataLine output;
    private final Thread worker;

    public MenuMusic(boolean silentCheck){
        this.silentCheck=silentCheck;
        if (MUSIC_ENABLED) {
            worker=new Thread(this::run,"xingclient-menu-music");worker.setDaemon(true);worker.start();
        } else {
            worker=null;
        }
    }
    public boolean isActive(){return active;}
    public long decodedFrames(){return frames;}
    public Throwable failure(){return failure;}
    public void setActive(boolean value){
        if(!MUSIC_ENABLED||active==value||closed)return;
        synchronized(gate){
            active=value;
            var line=output;if(line!=null){if(value)line.start();else line.stop();}
            gate.notifyAll();
        }
    }
    private boolean awaitMenu() throws InterruptedException {
        synchronized(gate){while(!active&&!closed)gate.wait();return !closed;}
    }
    private void run(){
        try {
            while(awaitMenu()){
                var resource=MenuMusic.class.getResourceAsStream("/assets/xingclient/audio/menu.mp3");
                if(resource==null)throw new IllegalStateException("Missing menu music");
                Player player=null;
                try(var stream=new BufferedInputStream(resource)){
                    player=new Player(stream,new FixedAudio());
                    while(awaitMenu()&&player.play(1))frames++;
                }finally{if(player!=null)player.close();}
            }
        }catch(Exception e){if(!closed){failure=e;LoggerFactory.getLogger("xingclient").error("Menu music could not play",e);}}
    }
    @Override public void close(){
        synchronized(gate){closed=true;active=false;gate.notifyAll();}
        var line=output;if(line!=null){line.stop();line.flush();line.close();}
        if(worker!=null)worker.interrupt();
    }
    private final class FixedAudio implements AudioDevice {
        private Decoder decoder;
        private boolean open;
        private byte[] bytes=new byte[8192];
        private SourceDataLine line;
        public void open(Decoder decoder){this.decoder=decoder;open=true;}
        public boolean isOpen(){return open;}
        public void write(short[] samples,int offset,int length) throws JavaLayerException {
            try {
                if(!awaitMenu())return;
                if(silentCheck){Thread.sleep(Math.max(1,1000L*length/decoder.getOutputChannels()/decoder.getOutputFrequency()));return;}
                if(line==null){
                    var format=new AudioFormat(decoder.getOutputFrequency(),16,decoder.getOutputChannels(),true,false);
                    line=AudioSystem.getSourceDataLine(format);line.open(format,(int)(format.getFrameRate()*format.getFrameSize()/10));
                    if(line.isControlSupported(FloatControl.Type.MASTER_GAIN)){
                        var gain=(FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
                        gain.setValue(Math.clamp(0f,gain.getMinimum(),gain.getMaximum()));
                    }
                    if(line.isControlSupported(FloatControl.Type.VOLUME)){
                        var volume=(FloatControl)line.getControl(FloatControl.Type.VOLUME);
                        volume.setValue(Math.clamp(1f,volume.getMinimum(),volume.getMaximum()));
                    }
                    synchronized(gate){output=line;if(closed){line.close();return;}if(active)line.start();}
                }
                if(bytes.length<length*2)bytes=new byte[length*2];
                for(int i=0;i<length;i++){short sample=samples[offset+i];bytes[i*2]=(byte)sample;bytes[i*2+1]=(byte)(sample>>8);}
                int sent=0;while(sent<length*2&&!closed){if(!awaitMenu())return;sent+=line.write(bytes,sent,length*2-sent);}
            }catch(Exception e){if(!closed)throw new JavaLayerException("Could not output menu audio",e);}
        }
        public void flush(){if(line!=null&&!closed)line.drain();}
        public void close(){open=false;if(line!=null){line.stop();line.close();if(output==line)output=null;}}
        public int getPosition(){return line==null?0:(int)(line.getMicrosecondPosition()/1000);}
    }
}
