/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vlc;

import javafx.event.Event;
import javafx.event.EventType;

/**
 *
 * @author kristof
 */
public class PlayerEvent extends Event {

    public enum Type {

        FINISHED
    }

    private final Type type;

    public PlayerEvent(Type type) {
        super(EventType.ROOT);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
