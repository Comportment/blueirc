package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an IRC user.
 * Contains informations about a user, well, it doesn't contain a lot for now
 * But it's meant to be improved to have more informations about the user.
 *
 */
public class User {
	static int bufferLength = Integer.MAX_VALUE;
	private String nick;
	private String prefix;
	private ArrayList<Parser> messages;
	
	User(String nick, String prefix) {
		this.nick = nick;
		this.prefix = prefix;
		messages = new ArrayList<>();
	}
	
	/**
	 * Add a message to the user messages list.
	 * 
	 * @param p The parser that contains the message
	 */
	public void addMessage(Parser p) {
		Util.addMessage(bufferLength, messages, p);
	}
	
	/**
	 * Gets user nick.
	 * 
	 * @return User nick
	 */
	public String getNick() {
		return nick;
	}
	
	/**
	 * Gets user prefix.
	 * Since this library supports IRCv3, if it's connected to an IRCv3 enabled server
	 * Then this method will return a multi-prefix
	 * 
	 * @return User prefix
	 */
	public String getPrefix() {
		return prefix;
	}
	
	/**
	 * Gets user messages.
	 * 
	 * @return User messages
	 */
	public List<Parser> getMessages() {
		return messages;
	}
}
