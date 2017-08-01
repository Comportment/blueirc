package tk.microdroid.blueirc;

import java.util.ArrayList;

class Util {

    static void addMessage(int bufferLength, ArrayList<Parser> messages, Parser p) {
        messages.add(p);
        while (messages.size() > bufferLength) {
            messages.remove(0);
        }
    }
}