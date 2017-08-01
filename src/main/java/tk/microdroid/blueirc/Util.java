package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Util {

    static void addMessage(int bufferLength, ArrayList<Parser> messages, Parser p) {
        messages.add(p);
        while (messages.size() > bufferLength) {
            messages.remove(0);
        }
    }

    static void addSplitArgs(List<String> list, String input) {
        addSplitArgs(list, "\\s+", input);
    }

    static void addSplitArgs(List<String> list, String split, String input) {
        list.addAll(Arrays.asList(input.split(split)));
    }
}