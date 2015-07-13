/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vlc;

import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import uk.co.caprica.vlcj.binding.internal.libvlc_media_t;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.TrackDescription;
import uk.co.caprica.vlcj.player.direct.BufferFormat;
import uk.co.caprica.vlcj.player.direct.BufferFormatCallback;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;
import uk.co.caprica.vlcj.player.direct.RenderCallback;
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;
import vlc.PlayerEvent.Type;

/**
 *
 * @author kristof
 */
public class VLCPlayer extends StackPane {

    private final MediaPlayer mediaPlayer;
    private final ResizableWritableImageView canvas;
    private final AtomicInteger frameNumber = new AtomicInteger();

    static {
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "C:\\Program Files\\VideoLAN\\VLC");
        /*new NativeDiscovery().discover();

         try {
         LibVlcVersion.getVersion();
         } catch (Exception e) {
         System.out.println("lib not found");
         }*/
    }

    private volatile boolean videoOutputStarted;

    public VLCPlayer(String... args) {

        List<String> arguments = new ArrayList<>(Arrays.asList(args));

        arguments.add("--no-plugins-cache");
        arguments.add("--no-snapshot-preview");
        arguments.add("--input-fast-seek");
        arguments.add("--no-video-title-show");
        arguments.add("--disable-screensaver");
        arguments.add("--network-caching");
        arguments.add("3000");
        arguments.add("--quiet");
        arguments.add("--quiet-synchro");
        arguments.add("--intf");
        arguments.add("dummy");

        MediaPlayerFactory factory = new MediaPlayerFactory(arguments);

        canvas = new ResizableWritableImageView(16, 16);
        getChildren().add(canvas);
        setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        final WritablePixelFormat<ByteBuffer> byteBgraInstance = PixelFormat.getByteBgraPreInstance();

        DirectMediaPlayer mp = factory.newDirectMediaPlayer(
                new BufferFormatCallback() {

                    @Override
                    public BufferFormat getBufferFormat(final int width, final int height) {
                        System.out.println("[FINE] VLCPlayer: Buffer size changed to " + width + "x" + height);

                        Platform.runLater(() -> {
                            canvas.resize(width, height);
                        });

                        return new RV32BufferFormat(width, height);
                    }
                },
                new RenderCallback() {
                    AtomicReference<ByteBuffer> currentByteBuffer = new AtomicReference<>();

                    @Override
                    public void display(DirectMediaPlayer mp, Memory[] memory, final BufferFormat bufferFormat) {
                        final int renderFrameNumber = frameNumber.incrementAndGet();

                        currentByteBuffer.set(memory[0].getByteBuffer(0, memory[0].size()));

                        Platform.runLater(() -> {
                            ByteBuffer byteBuffer = currentByteBuffer.get();
                            int actualFrameNumber = frameNumber.get();

                            if (renderFrameNumber == actualFrameNumber) {
                                canvas.getPixelWriter().setPixels(0, 0, bufferFormat.getWidth(), bufferFormat.getHeight(), byteBgraInstance, byteBuffer, bufferFormat.getPitches()[0]);
                            } else {
                                System.out.println("[FINE] " + VLCPlayer.this.getClass().getSimpleName() + " - Skipped late frame " + renderFrameNumber + " (actual = " + actualFrameNumber + ")");
                            }
                        });
                    }
                }
        );

        this.mediaPlayer = mp;

        mediaPlayer.addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            private final AtomicInteger ignoreFinish = new AtomicInteger();
            private boolean videoAdjusted;

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, final long newTime) {
                position.update(newTime);
                if (!videoAdjusted) {
                    mediaPlayer.setAdjustVideo(true);
                }
            }

            @Override
            public void lengthChanged(MediaPlayer mediaPlayer, long newLength) {
                length.update(newLength);
            }

            @Override
            public void newMedia(MediaPlayer mediaPlayer) {
                System.out.println("[FINE] VLCPlayer: Event[newMedia]");
                updateSubtitles();
                videoAdjusted = false;
            }

            @Override
            public void mediaChanged(MediaPlayer mediaPlayer, libvlc_media_t media, String mrl) {
                System.out.println("[FINE] VLCPlayer: Event[mediaChanged] '" + mrl + "': " + media);
            }

            @Override
            public void mediaMetaChanged(MediaPlayer mediaPlayer, int metaType) {
                // 12 = NowPlaying(?), 13 = Publisher(?), 15 = ArtworkURL(?)
            }

            @Override
            public void mediaStateChanged(final MediaPlayer mediaPlayer, int newState) {
                // IDLE/CLOSE=0, OPENING=1, BUFFERING=2, PLAYING=3, PAUSED=4, STOPPING=5, ENDED=6, ERROR=7
                if (newState >= 5) {
                    videoOutputStarted = false;
                }

                System.out.println("[FINE] VLCPlayer: Event[mediaStateChanged]: " + newState + "; volume=" + volume.get() + " --> " + mediaPlayer.getVolume() + "; mute=" + mediaPlayer.isMute());

                pausedProperty.update(newState == 4);
            }

            @Override
            public void mediaParsedChanged(MediaPlayer mediaPlayer, int parsed) {
                System.out.println("[FINE] VLCPlayer: Event[mediaParsedChanged]: " + parsed);
                if (parsed == 1) {
                    updateSubtitles();
                    audioTrack.update();
                }
            }

            @Override
            public void mediaSubItemAdded(MediaPlayer mediaPlayer, libvlc_media_t subItem) {
                ignoreFinish.incrementAndGet();
                int i = 1;

                System.out.println("VLCPlayer: mediaSubItemAdded: " + subItem.toString());

                for (TrackDescription desc : mediaPlayer.getTitleDescriptions()) {
                    System.out.println(i++ + " : " + desc.description());
                }
            }

            @Override
            public void videoOutput(final MediaPlayer mediaPlayer, int newCount) {
                System.out.println("VLCPlayer: videoOutput");

                mediaPlayer.setVolume(volume.get());
                mediaPlayer.mute(mutedProperty.get());

                videoOutputStarted = true;
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                if (ignoreFinish.get() == 0) {
                    videoOutputStarted = false;
                    System.out.println("VLCPlayer: Finished");
                    Events.dispatchEvent(onPlayerEvent, new PlayerEvent(Type.FINISHED), null);
                } else {
                    int index = mediaPlayer.subItemIndex();

                    List<String> subItems = mediaPlayer.subItems();

                    if (index >= 0 && index < subItems.size()) {
                        System.out.println("Finished: " + subItems.get(index));
                    } else {
                        index = 0;
                    }

                    ignoreFinish.decrementAndGet();
                    System.out.println("VLCPlayer: Adding more media");
                    mediaPlayer.playMedia(subItems.get(index));
                }
            }
        });

        length = new UpdatableLongProperty();
        position = new UpdatableLongProperty() {
            @Override
            public void set(long value) {
                mediaPlayer.setTime(value);
                super.set(value);
            }
        };
        volume = new BeanIntegerProperty(new Accessor<Integer>() {
            private int cachedVolume = 100;

            @Override
            public Integer read() {
                if (videoOutputStarted) {
                    cachedVolume = mediaPlayer.getVolume();
                }

                return cachedVolume;
            }

            @Override
            public void write(Integer value) {
                mediaPlayer.setVolume(value);
            }
        });
        audioDelay = new BeanIntegerProperty(new Accessor<Integer>() {
            @Override
            public void write(Integer value) {
                mediaPlayer.setAudioDelay(value * 1000L);
            }

            @Override
            public Integer read() {
                return (int) (mediaPlayer.getAudioDelay() / 1000);
            }
        });
        subtitleDelay = new BeanIntegerProperty(new Accessor<Integer>() {
            @Override
            public void write(Integer value) {
                mediaPlayer.setSpuDelay(-value * 1000L);
            }

            @Override
            public Integer read() {
                return (int) (-mediaPlayer.getSpuDelay() / 1000);
            }
        });
        rate = new BeanFloatProperty(mediaPlayer, "rate");
        brightness = new BeanFloatProperty(mediaPlayer, "brightness");
        mutedProperty = new BeanBooleanProperty(new Accessor<Boolean>() {
            private boolean cachedMuteStatus = false;

            @Override
            public Boolean read() {
                if (videoOutputStarted) {
                    cachedMuteStatus = mediaPlayer.isMute();
                }

                return cachedMuteStatus;
            }

            @Override
            public void write(Boolean value) {
                mediaPlayer.mute(value);
            }
        });
        pausedProperty = new BeanBooleanProperty(new Accessor<Boolean>() {
            @Override
            public Boolean read() {
                return false;
            }

            @Override
            public void write(Boolean value) {
                mediaPlayer.setPause(value);
            }
        });
    }

    public AudioTrack getAudioTrackInternal() {
        int index = mediaPlayer.getAudioTrack();

        if (index == -1 || index >= getAudioTracks().size()) {
            return AudioTrack.NO_AUDIO_TRACK;
        }
        return getAudioTracks().get(index);
    }

    public void setAudioTrackInternal(AudioTrack audioTrack) {
        mediaPlayer.setAudioTrack(getAudioTracks().indexOf(audioTrack));
    }

    public Subtitle getSubtitleInternal() {
        int id = mediaPlayer.getSpu();

        for (Subtitle subtitle : getSubtitles()) {
            if (subtitle.getId() == id) {
                return subtitle;
            }
        }

        return Subtitle.DISABLED;
    }

    public void setSubtitleInternal(Subtitle subtitle) {
        System.out.println("[FINE] VLCPlayer.setSubtitleInternal() - Subtitles available: " + getSubtitles());
        System.out.println("[FINE] VLCPlayer.setSubtitleInternal() - Setting subtitle to: " + subtitle + ", index = " + getSubtitles().indexOf(subtitle));
        mediaPlayer.setSpu(subtitle.getId());
    }

    private final ObservableList<Subtitle> subtitles = FXCollections.observableArrayList(Subtitle.DISABLED);

    public ObservableList<Subtitle> getSubtitles() {
        updateSubtitles();
        return FXCollections.unmodifiableObservableList(subtitles);
    }

    private final List<AudioTrack> audioTracks = new ArrayList<>();

    public ObservableList<AudioTrack> getAudioTracks() {
        if (audioTracks.isEmpty()) {
            for (TrackDescription description : mediaPlayer.getAudioDescriptions()) {
                audioTracks.add(new AudioTrack(description.id(), description.description()));
            }
        }

        return FXCollections.observableArrayList(audioTracks.isEmpty() ? NO_AUDIO_TRACKS : audioTracks);
    }

    private static final List<AudioTrack> NO_AUDIO_TRACKS = new ArrayList<>();

    public void play(String uri, long positionInMillis) {
        frameNumber.set(0);
        canvas.clear();
        position.update(0);
        audioDelay.update();
        audioTrack.update();
        brightness.update();
        rate.update();
        subtitle.update();
        subtitleDelay.update();

        List<String> arguments = new ArrayList<>();

        if (positionInMillis > 0) {
            arguments.add("start-time=" + positionInMillis / 1000);
        }

        mediaPlayer.setRepeat(false);
        mediaPlayer.setPlaySubItems(true);
        mediaPlayer.playMedia(uri, arguments.toArray(new String[arguments.size()]));

        System.out.println("[FINE] Playing: " + uri);
    }

    public void stop() {
        mediaPlayer.stop();
    }

    public void dispose() {
        mediaPlayer.release();
    }

    public ImageView getDisplayComponent() {
        return canvas;
    }

    public void showSubtitle(Path path) {
        System.out.println("[INFO] VLCPlayer.showSubtitle: path = " + path.toString());
        mediaPlayer.setSubTitleFile(path.toString());
    }

    private void updateSubtitles() {
        if (subtitles.size() > 1) {
            subtitles.subList(1, subtitles.size()).clear();
        }

        for (TrackDescription spuDescription : mediaPlayer.getSpuDescriptions()) {
            if (spuDescription.id() >= 0) {
                subtitles.add(new Subtitle(spuDescription.id(), spuDescription.description()));
            }
        }
    }

    private final UpdatableLongProperty length;

    public long getLength() {
        return length.get();
    }

    public ReadOnlyLongProperty lengthProperty() {
        return length;
    }

    private final UpdatableLongProperty position;

    public long getPosition() {
        return position.get();
    }

    public void setPosition(long position) {
        this.position.set(position);
    }

    public LongProperty positionProperty() {
        return position;
    }

    private final BeanIntegerProperty volume;

    public int getVolume() {
        return volume.get();
    }

    public void setVolume(int volume) {
        this.volume.set(volume);
    }

    public IntegerProperty volumeProperty() {
        return volume;
    }

    private final BeanIntegerProperty audioDelay;

    public int getAudioDelay() {
        return audioDelay.get();
    }

    public void setAudioDelay(int audioDelay) {
        this.audioDelay.set(audioDelay);
    }

    public IntegerProperty audioDelayProperty() {
        return audioDelay;
    }

    private final BeanFloatProperty rate;

    public float getRate() {
        return rate.get();
    }

    public void setRate(float rate) {
        this.rate.set(rate);
    }

    public FloatProperty rateProperty() {
        return rate;
    }

    private final BeanFloatProperty brightness;

    public float getBrightness() {
        return brightness.get();
    }

    public void setBrightness(float brightness) {
        this.brightness.set(brightness);
    }

    public FloatProperty brightnessProperty() {
        return brightness;
    }

    private final BeanObjectProperty<Subtitle> subtitle = new BeanObjectProperty<>(this, "subtitleInternal");

    public Subtitle getSubtitle() {
        return subtitle.get();
    }

    public void setSubtitle(Subtitle subtitle) {
        this.subtitle.set(subtitle);
    }

    public ObjectProperty<Subtitle> subtitleProperty() {
        return subtitle;
    }

    private final BeanObjectProperty<AudioTrack> audioTrack = new BeanObjectProperty<>(this, "audioTrackInternal");

    public AudioTrack getAudioTrack() {
        return audioTrack.get();
    }

    public void setAudioTrack(AudioTrack audioTrack) {
        this.audioTrack.set(audioTrack);
    }

    public ObjectProperty<AudioTrack> audioTrackProperty() {
        return audioTrack;
    }

    private final BeanIntegerProperty subtitleDelay;

    public int getSubtitleDelay() {
        return subtitleDelay.get();
    }

    public void setSubtitleDelay(int subtitleDelay) {
        this.subtitleDelay.set(subtitleDelay);
    }

    public IntegerProperty subtitleDelayProperty() {
        return subtitleDelay;
    }

    private final BooleanProperty mutedProperty;

    public boolean isMuted() {
        return mutedProperty.get();
    }

    public void setMuted(boolean muted) {
        mutedProperty.set(muted);
    }

    public BooleanProperty mutedProperty() {
        return mutedProperty;
    }

    private final BeanBooleanProperty pausedProperty;

    public boolean isPaused() {
        return pausedProperty.get();
    }

    public void setPaused(boolean paused) {
        pausedProperty.set(paused);
    }

    public BooleanProperty pausedProperty() {
        return pausedProperty;
    }

    private final ObjectProperty<EventHandler<PlayerEvent>> onPlayerEvent = new SimpleObjectProperty<>();

    public ObjectProperty<EventHandler<PlayerEvent>> onPlayerEvent() {
        return onPlayerEvent;
    }
}
