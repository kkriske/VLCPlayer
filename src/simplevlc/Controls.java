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
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
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
    private static final Image PLAY = new Image(Controls.class.getResourceAsStream("resources/play.png")),
            PAUSE = new Image(Controls.class.getResourceAsStream("resources/pause.png")),
            ENTERFS = new Image(Controls.class.getResourceAsStream("resources/enterfs.png")),
            EXITFS = new Image(Controls.class.getResourceAsStream("resources/exitfs.png"));

    @FXML
    private ImageView playpause, fullscreen;

    @FXML
    private AnchorPane controlpane;

    @FXML
    private ProgressBar progressbar;

    @FXML
    private Label currenttime, totaltime;

    public Controls(MediaPlayer mp, Stage primstage) {
        this.mp = mp;
        this.primstage = primstage;
        this.map = new HashMap<>();
        ts = new TimeStamp();
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

            updateTimeLabel(e);
        };

        EventHandler<MouseEvent> clickhandler = e -> {
            Node node = (Node) e.getTarget();
            if (node.getStyleClass().contains("track")
                    || node.getStyleClass().contains("bar")) {
                mp.setTime(calcTimeLabel(e));
            }
        };

        addEventFilter(MouseEvent.MOUSE_MOVED, movehandler);
        addEventFilter(MouseEvent.MOUSE_PRESSED, clickhandler);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, clickhandler);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, movehandler);

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
        });
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
        map.put(KeyCode.N, e -> fade.playFromStart());
        map.put(KeyCode.E, e -> mp.nextFrame());
    }

    @FXML
    private void playpause() {
        mp.setPause(mp.isPlaying());
    }

    private void fullscreen() {
        primstage.setFullScreen(!primstage.isFullScreen());
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

    private String formatNanoToTime(long time) {
        long seconds = time / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%d:%d:%d", hours, minutes % 60, seconds % 60);
    }

    private void updateTimeLabel(MouseEvent e) {
        Node node = (Node) e.getTarget();
        if (node.getStyleClass().contains("track")
                || node.getStyleClass().contains("bar")) {
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
