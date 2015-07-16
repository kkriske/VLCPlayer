/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vlcplayer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import simplevlc.SimpleVLCPlayer;

/**
 *
 * @author kristof
 */
public class VLCPlayer extends Application {

    @Override
    public void start(Stage primaryStage) {
        SimpleVLCPlayer root = new SimpleVLCPlayer(primaryStage);

        Scene scene = new Scene(root, 900, 500);

        primaryStage.setTitle("VLC Player");
        primaryStage.setScene(scene);
        primaryStage.setOnShown(e -> root.play("D:\\films\\Chappie (2015) [1080p]\\Chappie.2015.1080p.BluRay.x264.YIFY.mp4"));
        primaryStage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
