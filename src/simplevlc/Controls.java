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
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
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
        primstage.fullScreenProperty().addListener((s, o, n) -> fullscreen.setImage(n ? EXITFS : ENTERFS));
        playpause.setImage(PAUSE);
        fullscreen.setImage(ENTERFS);
        playpause.setOnMouseClicked(e -> playpause());
        fullscreen.setOnMouseClicked(e -> fullscreen());
        initKeyMapping();
        setOnKeyPressed(e -> {
            if (map.containsKey(e.getCode())) {
                map.get(e.getCode()).handle(e);
            }
        });

        mp.addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> playpause.setImage(PLAY));
            }

            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> playpause.setImage(PAUSE));
            }

            @Override
            public void mediaMetaChanged(MediaPlayer mediaPlayer, int metaType) {
                Platform.runLater(() -> totaltime.setText(formatNanoToTime(mp.getLength())));
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

    private void initKeyMapping() {
        map.put(KeyCode.F, e -> fullscreen());
        map.put(KeyCode.SPACE, e -> playpause());
        map.put(KeyCode.LEFT, e -> rewind(e));
        map.put(KeyCode.RIGHT, e -> forward(e));
        map.put(KeyCode.N, e -> mp.playMedia("D:\\films\\Just Before I Go (2014) [1080p]\\Just.Before.I.Go.2014.1080p.BluRay.x264.YIFY.mp4"));
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
}
