package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	boolean hasWhoInfo = false;
	private String nick="", prefix="", realName="", hostmask="", server="", username="";
	private ArrayList<Parser> messages = new ArrayList<Parser>();
	
	User(String nick, String prefix) {
		this.nick = nick;
		this.prefix = prefix;
	}
	
	User(String nick, HashMap<Character, Character> prefixes) {
		if (prefixes != null) {
			String[] parsedNick = parseNick(nick, prefixes);
			this.prefix = parsedNick[0];
			this.nick = parsedNick[1];
		} else {
			this.nick = nick;
			this.prefix = "";
		}
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
	 * Get user ident/login.
	 * 
	 * @return User login
	 */
	public String getUsername() {
		return username;
	}
	
	/**
	 * Get user's server.
	 * 
	 * It's the server the user connected on, not the plain url used to connect
	 * Something like wolfe.freenode.net
	 * 
	 * @return Remote server address
	 */
	public String getServer() {
		return server;
	}
	
	/**
	 * Get user's realname
	 * 
	 * @return User's realname
	 */
	public String getRealName() {
		return realName;
	}
	
	/**
	 * Set user's login
	 * 
	 * @param login User's login
	 */
	void setUsername(String username) {
		this.username = username;
	}
	
	/**
	 * Set user's hostname
	 * 
	 * @param hostname User's hostname
	 */
	void setHostname(String hostname) {
		this.hostmask = hostname;
	}
	
	/**
	 * Set user's server
	 * 
	 * @param server User's server
	 */
	void setServer(String server) {
		this.server = server;
	}
	
	/**
	 * Set user's realname
	 * 
	 * @param realName The realname
	 */
	void setRealName(String realName) {
		this.realName = realName;
	}
	
	/**
	 * Check if received a WHO response for this user.
	 * 
	 * @return True if we have all the info, otherwise false
	 */
	public boolean hasWhoInfo() {
		return hasWhoInfo;
	}
	
	/**
	 * Return all the informations in a String
	 * Used for debugging purposes
	 * 
	 * @return User's info string
	 */
	public String toString(){
		String output = "Nick: " + nick + " hostmask: " + hostmask + " realName: " + realName + " server: " + server + " prefix: " + prefix ;
		return output;
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
	
	public static String[] parseNick(String nick, HashMap<Character, Character> prefixes) {
		String prefixesStr = "";
		for (Character key : prefixes.keySet())
			prefixesStr += key;
		Pattern pattern = Pattern.compile("([" + prefixesStr + "]*)(\\w.+)");
		Matcher matcher = pattern.matcher(nick);
		boolean matches = matcher.matches();
		String prefix = matches ? matcher.group(1) : "";
		String rawName = matches ? matcher.group(2) : nick;
		for (Character c : prefixes.keySet())
			if (prefixes.containsKey(c))
				prefix = prefix.replace(c, prefixes.get(c));
		return new String[] {prefix, rawName};
	}
}
