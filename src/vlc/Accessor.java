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
public interface Accessor<T> {

    T read();

    void write(T value);
}
