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
	private String nick="", prefix="", realName="", hostmask="", server="", login="";
	private ArrayList<Parser> messages = new ArrayList<Parser>();
	
	User(String nick, String prefix) {
		this.nick = nick;
		this.prefix = prefix;
	}
	
	User(String nick, HashMap<Character, Character> prefixes) {
		if (prefixes != null) {
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
			this.nick = rawName;
			this.prefix = prefix;
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
	//Set and get methods for all parameters
	public String getNick() {
		return nick;
	}
	
	public String getLogin() {
		return login;
	}
	public String getServer() {
		return server;
	}
	public String getRealName() {
		return realName;
	}
	
	public void setLogin(String login) {
		this.login =  login;
	}
	public void setHostname(String hostname) {
		this.hostmask = hostname;
	}
	public void setServer(String server) {
		this.server = server;
	}
	public void setRealName(String realName) {
		this.realName = realName;
	}
	
	public boolean hasWhoInfo() {
		return hasWhoInfo;
	}

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
}
