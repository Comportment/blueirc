package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code Channel} simply represents an IRC channel
 * Each instance of {@code Channel} should represent an IRC channel, 
 * {@code Channel} contains everything a user needs to know about a channel
 * It contains Channel messages, users, the topic, join/last join dates, and
 * The name of the channel, the one passed from the constructor.
 * 
 * {@code Channel} instances are stored in an {@code ArrayList} in {@code Worker}.
 * 
 */
public class Channel {
	static int bufferLength = Integer.MAX_VALUE;
	private String name;
	private ArrayList<Parser> messages = new ArrayList<Parser>();
	private HashMap<String, User> users = new HashMap<>();
	private String topic = "";
	private Date firstJoinDate;
	private Date lastJoinDate;
	private boolean hasLeft = false;
	
	Channel(String name) {
		this.name = name;
		firstJoinDate = lastJoinDate = new Date();
	}
	
	/**
	 *  Gets the channel name.
	 * 
	 *  @return The channel name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Gets channel messages.
	 * 
	 * @return Channel messages
	 */
	public List<Parser> getMessages() {
		return messages;
	}
	
	/**
	 * Pushes a new message to the messages list.
	 * 
	 * @param p The Parser object containing the message
	 */
	public void addMessage(Parser p) {
		messages.add(p);
		while (messages.size() > bufferLength)
			messages.remove(0);
	}
	
	/**
	 * Checks is user in channel.
	 * 
	 * @param nick The nickname to check
	 * @return Boolean, true if user is in channel, otherwise false
	 */
	public boolean hasUser(String nick) {
		for (String n : users.keySet())
			if (n.equals(nick))
				return true;
		return false;
	}
	
	/**
	 * Gets users in this channel.
	 * 
	 * @return HashMap, User nicknames as keys, and User objects as values
	 * @see User
	 */
	public HashMap<String, User> getUsers() {
		return users;
	}
	
	/**
	 * Replaces the current channel topic with a new one.
	 * 
	 * @param newTopic The new topic
	 */
	void setTopic(String newTopic) {
		topic = newTopic;
	}
	
	/**
	 * Gets the current channel topic
	 * 
	 * @return Channel topic
	 */
	public String getTopic() {
		return topic;
	}
	
	/**
	 * Gets the first time we joined this channel.
	 * 
	 * @return The date we first joined this channel
	 */
	public Date getFirstJoinDate() {
		return firstJoinDate;
	}
	
	/**
	 * Gets the date of the last time we joined this channel.
	 * This is useless when channels are not preserved after leaving them
	 * 
	 * @return The date of the last join
	 */
	public Date getLastJoinDate() {
		return lastJoinDate;
	}
	
	/**
	 * Reconfigure upon channel rejoin.
	 * Sets few variables, this is invoked when joining a preserved channel
	 */
	void rejoin() {
		lastJoinDate = new Date();
		hasLeft = false;
	}
	
	/**
	 * Checks if this channel has been left.
	 * 
	 * @return Whether we left this channel or not
	 */
	public boolean hasLeft() {
		return hasLeft;
	}
	
	/**
	 * Configure upon leaving channel.
	 * This makes no sense if channels are not preserved
	 */
	void leave() {
		hasLeft = true;
	}
	
	/**
	 * Adds a user to the user list.
	 * Creates a new User object and adds it to the users HashMap
	 * This splits the prefixes from nicks, provided in {@code name}
	 * Invalid {@code prefixes} could make {@code name} parsing crash
	 * 
	 * @param nick The user nickname
	 * @param prefixes The prefixes supported by the server, parsed from 005
	 */
	void addUser(String nick, HashMap<Character, Character> prefixes) {
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
				prefix.replace(c, prefixes.get(c));
		
		User user = new User(rawName, prefix);
		users.put(rawName, user);
	}
	
	/**
	 * Removes a joined user.
	 * 
	 * @param nick The user nickname
	 */
	void removeUser(String nick) {
		if (users.containsKey(nick))
			users.remove(nick);
	}
}
