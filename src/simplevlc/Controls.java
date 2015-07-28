/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simplevlc;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter;

/**
 *
 * @author kristof
 */
public class Controls extends AnchorPane implements Initializable {

    private final Stage primstage;
    private final Map<KeyCode, EventHandler<KeyEvent>> map;
    private final MediaPlayer mp;
    private final TimeStamp ts;
    private FadeTransition fade;
    private boolean finished;
    private final BooleanProperty mutedProperty;
    private final DoubleProperty volumeProperty;
    private static final Image PLAY = new Image(Controls.class.getResourceAsStream("resources/play.png")),
            PAUSE = new Image(Controls.class.getResourceAsStream("resources/pause.png")),
            ENTERFS = new Image(Controls.class.getResourceAsStream("resources/enterfs.png")),
            EXITFS = new Image(Controls.class.getResourceAsStream("resources/exitfs.png")),
            VOLUMEMUTED = new Image(Controls.class.getResourceAsStream("resources/volumemuted.png")),
            VOLUMELOW = new Image(Controls.class.getResourceAsStream("resources/volumelow.png")),
            VOLUMEMEDIUM = new Image(Controls.class.getResourceAsStream("resources/volumemedium.png")),
            VOLUMEHIGH = new Image(Controls.class.getResourceAsStream("resources/volumehigh.png")),
            VOLUMEHIGHEST = new Image(Controls.class.getResourceAsStream("resources/volumehighest.png"));

    @FXML
    private ImageView playpause, fullscreen, volume;

    @FXML
    private AnchorPane controlpane;

    @FXML
    private ProgressBar progressbar;

    @FXML
    private Text currenttime, totaltime;

    @FXML
    private Circle thumb;

    @FXML
    private Slider volumeslider;

    public Controls(MediaPlayer mp, Stage primstage) {
        this.mp = mp;
        this.primstage = primstage;
        this.map = new HashMap<>();
        finished = false;
        ts = new TimeStamp();
        mutedProperty = new SimpleBooleanProperty(false);
        volumeProperty = new SimpleDoubleProperty();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("resources/controls.fxml"));
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException ex) {
            System.err.println("failed to load Controls");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        primstage.setFullScreenExitHint("");
        fullscreen.imageProperty().bind(Bindings.when(primstage.fullScreenProperty()).<Image>then(EXITFS).otherwise(ENTERFS));
        playpause.setImage(PAUSE);
        playpause.setOnMouseClicked(e -> playpause());
        fullscreen.setOnMouseClicked(e -> fullscreen());
        initKeyMapping();
        setOnKeyPressed(e -> {
            if (isFocused() && map.containsKey(e.getCode())) {
                map.get(e.getCode()).handle(e);
            }
        });

        volumeProperty.addListener((s, o, n) -> mp.setVolume((int) (double) n));
        volumeProperty.set(100);
        DoubleProperty dummy = new SimpleDoubleProperty(0);
        mutedProperty.addListener((s, o, n) -> {
            if (n) {
                volumeslider.valueProperty().bind(dummy);
            } else {
                volumeslider.valueProperty().unbind();
                volumeslider.setValue(volumeProperty.get());
            }
        });
        volumeProperty.addListener((s, o, n) -> {
            //  try {
            volumeslider.setValue((double) n);
            //} catch (Exception ex) {
            //  System.out.println(ex);
            // }
        });
        //volumeslider.valueProperty().bind(Bindings.when(mutedProperty).then(0).otherwise(volumeProperty));
        //binding moet weg
        volumeslider.setValue(100);
        volumeslider.setVisible(false);
        volumeslider.valueProperty().addListener((s, o, n) -> {
            mp.setVolume((int) (double) n);
        });

        mutedProperty.addListener((s, o, n) -> mp.mute(n));

        volume.setOnMouseClicked(e -> mute());
        volume.imageProperty().bind(
                Bindings.when(mutedProperty)
                .<Image>then(VOLUMEMUTED)
                .otherwise(
                        Bindings.when(volumeslider.valueProperty().greaterThan(100))
                        .<Image>then(
                                Bindings.when(volumeslider.valueProperty().greaterThan(150))
                                .<Image>then(VOLUMEHIGHEST)
                                .otherwise(VOLUMEHIGH)
                        )
                        .otherwise(
                                Bindings.when(volumeslider.valueProperty().greaterThan(50))
                                .<Image>then(VOLUMEMEDIUM)
                                .otherwise(VOLUMELOW)
                        )
                )
        );

        final Cursor cursor = getCursor();
        cursorProperty().bind(Bindings.when(controlpane.visibleProperty()).<Cursor>then(cursor).otherwise(Cursor.NONE));

        fade = new FadeTransition(Duration.millis(300), controlpane);
        fade.setToValue(0);
        fade.setFromValue(1);
        fade.setDelay(Duration.millis(500));
        fade.setOnFinished(e -> controlpane.setVisible(false));

        ts.setVisible(false);
        controlpane.getChildren().add(ts);

        EventHandler<MouseEvent> movehandler = e -> {
            fade.stop();
            if (e.getTarget().equals(this)) {
                fade.playFromStart();
            }
            controlpane.setVisible(true);
            controlpane.setOpacity(1);
        };

        EventHandler<MouseEvent> clickhandler = e -> {
            //Node node = (Node) e.getTarget();
            /*if (node.getStyleClass().contains("track")
             || node.getStyleClass().contains("bar")) {*/
            if (progressbar.getChildrenUnmodifiable().contains((Node) e.getTarget())) {
                checkfinished();
                long time = calcTimeLabel(e);

                mp.setTime(time);
                progressbar.setProgress(((double) time) / mp.getLength());

            }
        };

        addEventFilter(MouseEvent.MOUSE_MOVED, movehandler);
        addEventFilter(MouseEvent.MOUSE_MOVED, e -> updateTimeLabel(e));
        addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (e.getTarget().equals(volume) || e.getTarget().equals(volumeslider) || volumeslider.getChildrenUnmodifiable().contains((Node) e.getTarget())) {
                volumeslider.setVisible(true);
            } else {
                volumeslider.setVisible(false);
            }
        });
        addEventFilter(MouseEvent.MOUSE_PRESSED, clickhandler);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, clickhandler);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> updateTimeLabel(e));
        addEventFilter(ScrollEvent.SCROLL, e -> {
            if (mutedProperty.get()) {
                return;
            }
            int newvol = mp.getVolume() + (e.getDeltaY() > 0 ? 5 : -5);
            if (newvol <= 200) {
                mp.setVolume(newvol);
                volumeslider.setValue(newvol);
            }
        });

        thumb.setLayoutY(progressbar.getLayoutY());
        ChangeListener<Number> thumbPosition = (s, o, n) -> thumb.setLayoutX(progressbar.getLayoutX() + progressbar.getProgress() * progressbar.getWidth());
        progressbar.layoutYProperty().addListener((s, o, n) -> thumb.setLayoutY((progressbar.getHeight() / 2) + (double) n));
        progressbar.progressProperty().addListener(thumbPosition);
        progressbar.widthProperty().addListener(thumbPosition);
        progressbar.layoutXProperty().addListener(thumbPosition);
        thumb.setMouseTransparent(true);

        mp.addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> playpause.setImage(PLAY));
                removeEventFilter(MouseEvent.MOUSE_MOVED, movehandler);
                setOnMouseMoved(null);
                fade.stop();
                controlpane.setVisible(true);
                controlpane.setOpacity(1);
            }

            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> playpause.setImage(PAUSE));
                addEventHandler(MouseEvent.MOUSE_MOVED, movehandler);
                fade.playFromStart();
                controlpane.setVisible(true);
            }

            @Override
            public void mediaMetaChanged(MediaPlayer mediaPlayer, int metaType) {
                Platform.runLater(() -> totaltime.setText(formatNanoToTime(mp.getLength())));
                fade.playFromStart();
                controlpane.setVisible(true);
            }

            @Override
            public void positionChanged(MediaPlayer mediaPlayer, float newPosition) {
                Platform.runLater(() -> {
                    progressbar.setProgress(newPosition);
                    currenttime.setText(formatNanoToTime(mp.getTime()));
                });
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                mp.prepareMedia(mp.mrl());
                Platform.runLater(() -> {
                    progressbar.setProgress(0);
                    currenttime.setText(formatNanoToTime(0));
                });
            }
        });
    }

    private void checkfinished() {
        if (finished) {
            mp.play();
            finished = false;
        }
    }

    public BooleanProperty controlsVisibleProperty() {
        return controlpane.visibleProperty();
    }

    public DoubleProperty controlsOpacityProperty() {
        return controlpane.opacityProperty();
    }

    private void initKeyMapping() {
        map.put(KeyCode.F, e -> fullscreen());
        map.put(KeyCode.SPACE, e -> playpause());
        map.put(KeyCode.LEFT, e -> rewind(e));
        map.put(KeyCode.RIGHT, e -> forward(e));
        map.put(KeyCode.UP, e -> volumeUp(e));
        map.put(KeyCode.DOWN, e -> volumeDown(e));
        map.put(KeyCode.E, e -> mp.nextFrame());
        map.put(KeyCode.M, e -> mute());
    }

    @FXML
    private void playpause() {
        mp.setPause(mp.isPlaying());
        checkfinished();
    }

    private void fullscreen() {
        primstage.setFullScreen(!primstage.isFullScreen());
    }

    private void mute() {
        mutedProperty.set(!mutedProperty.get());
    }

    private void rewind(KeyEvent e) {
        if (e.isControlDown()) {
            long time = mp.getTime() - 5000;
            mp.setTime(time < 0 ? 0 : time);
        }
    }

    private void forward(KeyEvent e) {
        if (e.isControlDown()) {
            long time = mp.getTime() + 5000;
            mp.setTime(time > mp.getLength() ? mp.getLength() : time);
        }
    }

    private void volumeUp(KeyEvent e) {
        if (e.isControlDown()) {
            double newVolume = volumeProperty.get() + 5;
            if (newVolume <= 200) {
                volumeProperty.set(newVolume);
            }
        }
    }

    private void volumeDown(KeyEvent e) {
        if (e.isControlDown()) {
            double newVolume = volumeProperty.get() - 5;
            if (newVolume >= 0) {
                volumeProperty.set(newVolume);
            }
        }
    }

    private String formatNanoToTime(long time) {
        long seconds = time / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%d:%d:%d", hours, minutes % 60, seconds % 60);
    }

    private void updateTimeLabel(MouseEvent e) {
        /*Node node = (Node) e.getTarget();
         if (node.getStyleClass().contains("track")
         || node.getStyleClass().contains("bar")) {*/
        if (progressbar.getChildrenUnmodifiable().contains((Node) e.getTarget())) {
            ts.setText(formatNanoToTime(calcTimeLabel(e)));
            ts.setVisible(true);
        } else {
            if (ts.isVisible()) {
                ts.setVisible(false);
            }
        }
    }

    private long calcTimeLabel(MouseEvent e) {
        double x = e.getSceneX();
        ts.relocate(x - ts.getWidth() / 2, progressbar.getLayoutY() - ts.getHeight() + 2);
        x -= progressbar.getLayoutX();
        x /= progressbar.getWidth();
        x = x > 1 ? 1 : x < 0 ? 0 : x;
        x *= mp.getLength();
        return (long) x;
    }
}
