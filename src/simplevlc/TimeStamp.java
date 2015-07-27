/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simplevlc;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 *
 * @author kristof
 */
public class TimeStamp extends VBox {

    @FXML
    private Label text;

    public TimeStamp() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("resources/timestamp.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException ex) {
            System.err.println("failed to create MovieTimeStamp");
        }
    }

    public void setText(String value) {
        text.setText(value);
    }
}
