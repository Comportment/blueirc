package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an IRC user.
 * Contains informations about a user, well, it doesn't contain a lot for now
 * But it's meant to be improved to have more informations about the user.
 *
 * @see Channel
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
		messages = new ArrayList<Parser>();
	}
	
	/**
	 * Add a message to the user messages list.
	 * 
	 * @param p The parser that contains the message
	 */
	public void addMessage(Parser p) {
		messages.add(p);
		while (messages.size() > bufferLength)
			messages.remove(0);
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
	
	/**
	 * Update the user nick
	 * 
	 * @param nick The new nickname
	 */
	public void updateNick(String nick) {
		this.nick = nick;
	}
}
