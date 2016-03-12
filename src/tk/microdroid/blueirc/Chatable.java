package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.HashMap;

public interface Chatable {
	public ChatType getType();
	public String getTitle();
	public HashMap<String, User> getParticipants();
	public ArrayList<PrivateMessage> getChatMessages();
}
