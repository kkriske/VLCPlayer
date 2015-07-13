/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vlc;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;

/**
 *
 * @author kristof
 */
public class UpdatableLongProperty extends SimpleLongProperty {

    public void update(final long value) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                UpdatableLongProperty.super.set(value);
            }
        });
    }
}
