/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simplevlc;

import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.direct.BufferFormat;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;
import uk.co.caprica.vlcj.player.direct.RenderCallback;
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

/**
 *
 * @author kristof
 */
public class SimpleVLCPlayer extends StackPane {

    final private ImageView canvas;
    final private WritableImage img;
    final private MediaPlayer mp;
    final private Controls controls;
    final private SimpleDoubleProperty ratio;

    private final static int MIN_WIDTH = 400,
            MIN_HEIGHT = 300;

    private final static String[] ARGUMENTS
            = new String[]{
                "--no-plugins-cache",
                "--no-snapshot-preview",
                "--input-fast-seek",
                "--no-video-title-show",
                "--disable-screensaver",
                "--network-caching",
                "3000",
                "--quiet",
                "--quiet-synchro",
                "--intf",
                "dummy"
            };

    static {
        //System.out.println(NativeLibrary.getInstance(RuntimeUtil.getLibVlcLibraryName()));
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "C:\\Program Files\\VideoLAN\\VLC");
    }

    public SimpleVLCPlayer(Stage primstage) {
        this(primstage, true);
    }

    public SimpleVLCPlayer(Stage primstage, boolean controlsVisible) {
        this(primstage, controlsVisible, ARGUMENTS);
    }

    public SimpleVLCPlayer(Stage primstage, boolean controlsVisible, String... args) {
        //init visual content
        final Dimension bounds = getMaxBounds();
        img = new WritableImage((int) bounds.getWidth(), (int) bounds.getHeight());
        canvas = new ImageView(img);
        super.getChildren().add(canvas);
        setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        primstage.setOnCloseRequest(e -> release());

        //init size stuff
        ratio = new SimpleDoubleProperty(Double.NaN);
        initSize(primstage);

        //init MediaPlayer
        mp = new MediaPlayerFactory(args).newDirectMediaPlayer(
                (w, h) -> {
                    Platform.runLater(() -> ratio.set((double) h / (double) w));
                    return new RV32BufferFormat((int) bounds.getWidth(), (int) bounds.getHeight());
                }, new Renderer());

        //init controls
        controls = new Controls(mp, primstage);
        heightProperty().addListener((s, o, n) -> controls.setPrefHeight((double) n));
        widthProperty().addListener((s, o, n) -> controls.setPrefWidth((double) n));
        super.getChildren().add(controls);
        setControlsVisible(controlsVisible);

        setOnMouseClicked(e -> controls.requestFocus());
    }

    private Dimension getMaxBounds() {
        Dimension dim = new Dimension(0, 0);
        Screen.getScreens().stream().map((s) -> s.getVisualBounds()).forEach((bounds) -> {
            dim.setSize(
                    dim.getWidth() < bounds.getWidth() ? bounds.getWidth() : dim.getWidth(),
                    dim.getHeight() < bounds.getHeight() ? bounds.getHeight() : dim.getHeight()
            );
        });
        return dim;
    }

    private void initSize(Stage primstage) {
        primstage.minWidthProperty().addListener((s, o, n) -> {
            if ((double) n < MIN_WIDTH) {
                primstage.setMinWidth(MIN_WIDTH);
            }
        });
        primstage.minHeightProperty().addListener((s, o, n) -> {
            if ((double) n < MIN_HEIGHT) {
                primstage.setMinHeight(MIN_HEIGHT);
            }
        });
        if (primstage.getMinHeight() < MIN_HEIGHT) {
            primstage.setMinHeight(MIN_HEIGHT);
        }
        if (primstage.getMinWidth() < MIN_WIDTH) {
            primstage.setMinWidth(MIN_WIDTH);
        }
        setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        widthProperty().addListener((s, o, n) -> updateSize());
        heightProperty().addListener((s, o, n) -> updateSize());
        ratio.addListener(o -> updateSize());
    }

    private void updateSize() {
        if (!ratio.getValue().isNaN()) {
            double w = getWidth(), h = getHeight(), r = ratio.get();
            double fith = r * w;
            if (fith > h) {
                canvas.setFitHeight(h);
                canvas.setFitWidth(h / r);
            } else {
                canvas.setFitHeight(fith);
                canvas.setFitWidth(w);
            }
        }
    }

    final public MediaPlayer getMediaPlayer() {
        return mp;
    }

    final public void setControlsVisible(boolean visible) {
        controls.setVisible(visible);
    }

    final public void play(String url) {
        mp.playMedia(url);
    }

    final public void release() {
        mp.release();
    }

    private class Renderer implements RenderCallback {

        final AtomicReference<ByteBuffer> currentByteBuffer = new AtomicReference<>();
        final WritablePixelFormat<ByteBuffer> byteBgraInstance = PixelFormat.getByteBgraPreInstance();
        final AtomicInteger frameNumber = new AtomicInteger(0);

        @Override
        public void display(DirectMediaPlayer dmp, Memory[] nativeBuffers, BufferFormat bf) {
            final int renderFrameNumber = frameNumber.incrementAndGet();
            currentByteBuffer.set(nativeBuffers[0].getByteBuffer(0, nativeBuffers[0].size()));

            Platform.runLater(() -> {
                ByteBuffer byteBuffer = currentByteBuffer.get();
                int actualFrameNumber = frameNumber.get();

                if (renderFrameNumber == actualFrameNumber) {
                    img.getPixelWriter().setPixels(0, 0, bf.getWidth(), bf.getHeight(), byteBgraInstance, byteBuffer, bf.getPitches()[0]);
                } else {
                    System.out.println(
                            String.format("%s - Skipped late frame %d (actual = %d)",
                                    SimpleVLCPlayer.this.getClass().getSimpleName(),
                                    renderFrameNumber,
                                    actualFrameNumber
                            )
                    );
                }
            });
        }

    }
}
