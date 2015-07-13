/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vlc;

/**
 *
 * @author kristof
 */
public class AudioTrack {

    public static final AudioTrack NO_AUDIO_TRACK = new AudioTrack(-1, "Unavailable");

    private final int id;
    private final String description;

    public AudioTrack(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getId() {
        return id;
    }
}
