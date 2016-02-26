package tk.microdroid.blueirc;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * This is the heart of this library, each instance of {@code Worker}.
 * Represents a whole instance of this library, each {@code Worker} is independent
 * Of another, you can run multiple instances of {@code Worker} at once.
 * 
 * You create an instance by passing server connection informations, informations are
 * NOT validated, you MUST validate the informations before creating an instance of {@code Worker}.
 *
 * Each {@code Worker} runs two {@code Threads}, one main thread and one for writing.
 * Everything is managed here, all the other classes are containers of helpers, however,
 * I think this should be split into multiple classes.
 * 
 * @see IO
 * @see Parser
 * @see IEventHandler
 * 
 */
public class Worker {
	private final static AtomicInteger idGen = new AtomicInteger(0);

	private ServerInfo serverInfo;
	private Thread writerThread;
	private Thread mainThread;
	private IO io;

	private IEventHandler eventHandler = new IEventHandler() {
		@Override
		public void onEvent(Event event, Object args) {
			// Nothing.
		}
	};
	private Socket socket;
	private SSLSocket sslSocket;
	private BlockingQueue<Message> writingQueue = new ArrayBlockingQueue<Message>(
			32);
	private boolean usingSecondNick = false;

	private boolean ircv3Support = false;
	private String[] ircv3Capabilities = {};
	private HashMap<Character, Character> prefixes = new HashMap<>(); // Server prefixes, from 005
	private String serverName; // The server name received from 005
	private StringBuilder motd;

	private HashMap<String, Channel> chans = new HashMap<>();
	private boolean preserveChannels = false;

	private long lag = 0;
	private long lagStart = 0, userLagStart = 0;
	private int lagPingId = 0, userLagPingId = 0;
	private boolean finishedLagMeasurement = true, finishedUserLagMeasurement = true;
	private Timer lagTimer = new Timer();
	private final String lagPrefix = "blueirc.", userLagPrefix = "blueirc.user.";

	public Worker(String server, int port, String nick, String secondNick,
			String username, String nickservPass, String serverPass,
			boolean ssl, boolean invalidSSL) {
		serverInfo = new ServerInfo();
		serverInfo.server = server;
		serverInfo.port = port;
		serverInfo.nick = nick;
		serverInfo.secondNick = secondNick;
		serverInfo.username = username;
		serverInfo.nickservPass = nickservPass;
		serverInfo.serverPass = serverPass;
		serverInfo.ssl = ssl;
		serverInfo.invalidSSL = invalidSSL;
		io = new IO();
	}

	public Worker(ServerInfo info) {
		serverInfo = info;
		io = new IO();
	}

	/**
	 * Start the {@code Worker} instance.
	 */
	public void start() {
		mainThread = new Thread(new IRCWorkerRunnable());
		mainThread.start();
	}

	/**
	 * This runnable is created and ran upon {@link #start()}.
	 *
	 * This runnable connects, maintains, manages and handles the IRC
	 * Commands of the IRC server.
	 */
	class IRCWorkerRunnable implements Runnable {
		@Override
		public void run() {
			try {
				if (serverInfo.ssl) {
					SSLSocketFactory sslFactory;
					if (serverInfo.invalidSSL) {
						// For now this ignores the certificate, however, this will be changed to do certificate
						// pinning (i.e. generating a fingerprint and asking the user to accept it or not,
						// rather than just ignoring it
						TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
							public java.security.cert.X509Certificate[] getAcceptedIssuers() {
								return new X509Certificate[0];
							}

							public void checkClientTrusted(
									java.security.cert.X509Certificate[] certs,
									String authType) {
							}

							public void checkServerTrusted(
									java.security.cert.X509Certificate[] certs,
									String authType) {
							}
						} };
						SSLContext sc = SSLContext.getInstance("SSL");
						sc.init(null, trustAllCerts,
								new java.security.SecureRandom());
						sslFactory = sc.getSocketFactory();
					} else
						sslFactory = (SSLSocketFactory) SSLSocketFactory
								.getDefault();
					sslSocket = (SSLSocket) sslFactory.createSocket(
							serverInfo.server, serverInfo.port);
				} else {
					socket = new Socket(serverInfo.server, serverInfo.port);
				}
				io.initialize(serverInfo.ssl ? sslSocket : socket);
				send("CAP LS");
				// Writes data in writingQueue to the socket
				writerThread = new Thread(new Runnable() {
					@Override
					public void run() {
						while (true) {
							Message msg = new Message("");
							try {
								msg = writingQueue.take();
								io.write(msg.getMsg());
								eventHandler.onEvent(Event.DATA_SENT, msg);
							} catch (Exception e) {
								eventHandler.onEvent(Event.DATA_SEND_FAIL,
										msg == null ? new Message("") : msg);
							}
						}
					}
				});
				writerThread.start();
				while (io.read() != null) {
					Parser p = new Parser(io.line);
					eventHandler.onEvent(Event.DATA_RECEIVED, p);
					if (p.action.equals("PING")) {
						send("PONG" + io.line.substring(4));
					} else if (p.action.equals("PONG")) { // Used in lag measurement
						if (p.msg.equals(lagPrefix + lagPingId)) {
							finishedLagMeasurement = true;
							lag = System.currentTimeMillis() - lagStart;
							eventHandler.onEvent(Event.LAG_MEASURED, lag);
						} else if (p.msg.equals(userLagPrefix + userLagPingId)) {
							finishedUserLagMeasurement = true;
							eventHandler.onEvent(Event.USER_LAG_MEASURED, System.currentTimeMillis() - userLagStart);
						}
					} else if (p.action.equals("PRIVMSG") // Add new message to chan and user
							&& (p.actionArgs.get(0).matches("[\\#\\&].+"))) { // Starts with [#&] as in rfc1459#section-1.3
						Channel chan = chans.get(p.actionArgs.get(0));
						chan.getUsers().get(p.nick).addMessage(p);
						chan.addMessage(p);
					} else if (p.action.equals("CAP")) {
						ircv3Support = true;
						String capType = p.actionArgs.get(1);
						if (capType.equals("LS")) {
							ircv3Capabilities = p.msg.split(" ");
							String[] reqCpbs = { "multi-prefix" }; // Requested capabilities
							for (String reqCpb : reqCpbs)
								if (hasCapability(reqCpb))
									send("CAP REQ " + reqCpb);
							send("CAP END");
							register(serverInfo.nick);
						} else if (capType.equals("NAK"))
							eventHandler.onEvent(
									Event.IRCV3_CAPABILITY_REJECTED, p.msg);
						else if (capType.equals("ACK"))
							eventHandler.onEvent(
									Event.IRCV3_CAPABILITY_ACCEPTED, p.msg);
					} else if (p.action.equals("JOIN")) { // Create new Channel
						if (!chans.containsKey(p.msg))
							chans.put(p.msg, new Channel(p.msg));
						else
							chans.get(p.msg).rejoin();
					} else if (p.action.equals("PART")) { // Remove channel (When not preserving them) or user in channel
						if (p.nick
								.equals(usingSecondNick ? serverInfo.secondNick
										: serverInfo.nick)) {
							eventHandler.onEvent(Event.LEFT_CHANNEL,
									p.actionArgs.get(0));
							chans.get(p.actionArgs.get(0)).leave();
							if (!preserveChannels)
								chans.remove(p.actionArgs.get(0));
						} else {
							chans.get(p.actionArgs.get(0)).removeUser(p.nick);
						}
					} else if (p.action.equals("NICK")) { // Update nicknames upon nicknames change
						if (p.msg.equals(serverInfo.nick))
							serverInfo.nick = p.msg;
						for (Channel chan : chans.values())
							for (User user : chan.getUsers().values())
								if (user.getNick().equals(p.msg))
									user.updateNick(p.msg);
					} else if (p.action.equals("KICK")) { // Remove channel (If not preserving them) or user in channel
						if (p.actionArgs.get(1).equals(
								usingSecondNick ? serverInfo.secondNick
										: serverInfo.nick)) {
							eventHandler.onEvent(Event.KICKED, p);
							chans.get(p.actionArgs.get(0)).leave();
							if (!preserveChannels)
								chans.remove(p.actionArgs.get(0));
						} else {
							chans.get(p.actionArgs.get(0)).removeUser(
									p.actionArgs.get(1));
						}
					} else if (p.action.equals("QUIT")) { // Remove user from all the channels
						for (Channel chan : chans.values()) {
							chan.removeUser(p.nick);
						}
					} else if (p.action.equals("TOPIC") && chans.containsKey(p.actionArgs.get(0))) { // Update topic upon change
						chans.get(p.actionArgs.get(0)).setTopic(p.msg);
					} else if (p.numberAction.equals("332") && chans.containsKey(p.actionArgs.get(1))) { // The topic sent upon join
						chans.get(p.actionArgs.get(1)).setTopic(p.msg);
					} else if (p.numberAction.equals("001")) { // Welcome to the server
						eventHandler.onEvent(Event.CONNECTED, p.server);
						lagTimer.schedule(new LagPing(), 0, 30000);
					} else if (p.numberAction.equals("421")
							&& p.actionArgs.get(1).equals("CAP")) { // Unknown command CAP (i.e. the server doesn't support IRCv3)
						register(serverInfo.nick);
					} else if (p.numberAction.equals("353")) { // NAMES response
						if (chans.containsKey(p.actionArgs.get(2))) {
							Channel chan = chans.get(p.actionArgs.get(2));
							for (String user : p.msg.split(" "))
								chan.addUser(user, prefixes);
						}
					} else if (p.numberAction.equals("005")) { // Server capabilities, sent upon connection
						for (String spec : p.actionArgs) {
							String[] kvSplitter = spec.split("=", 2);
							String key = kvSplitter[0].toUpperCase();
							String value = kvSplitter.length == 2 ? kvSplitter[1]
									: "";
							switch (key) {
							case "PREFIX":
								String[] splitter = value.substring(1).split("\\)");
								for (int i = 0; i < splitter[0].length(); i++) {
									prefixes.put(splitter[1].charAt(i),
											splitter[0].charAt(i));
								}
								break;
							case "NETWORK":
								serverName = value;
								eventHandler.onEvent(Event.GOT_SERVER_NAME,
										value);
							}
						}
					} else if (p.numberAction.equals("375")) { // Start of MOTD
						motd = new StringBuilder();
					} else if (p.numberAction.equals("372")) { // MOTD message
						motd.append("\n" + p.msg);
					} else if (p.numberAction.equals("376")) { // End of MOTD
						motd.trimToSize();
						eventHandler.onEvent(Event.GOT_MOTD, motd.toString()
								.substring(1));
					} else if (p.numberAction.equals("366")) { // Channel joined
						eventHandler.onEvent(Event.JOINED_CHANNEL, p.actionArgs.get(1));
					} else if (p.numberAction.equals("433")) { // Nickname in use
						if (!usingSecondNick) {
							eventHandler.onEvent(Event.FIRST_NICK_IN_USE,
									serverInfo.nick);
							register(serverInfo.secondNick);
						} else {
							eventHandler.onEvent(Event.ALL_NICKS_IN_USE,
									serverInfo.secondNick);
							break;
						}
					}
				}
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				// Never's gonna happen
			} catch (UnknownHostException e) {
				eventHandler.onEvent(Event.UNKNOWN_HOST, e);
			} catch (SocketTimeoutException e) {
				eventHandler.onEvent(Event.TIMEOUT, e);
			} catch (IOException e) {
				eventHandler.onEvent(Event.UNKNOWN_ERROR, e);
			} finally {
				eventHandler.onEvent(Event.DISCONNECTED, serverInfo.server);
				lagTimer.cancel();
				if (writerThread != null)
					writerThread.interrupt();
				try {
					if (serverInfo.ssl) sslSocket.close();
					else socket.close();
				} catch (IOException e) {
					
				}
			}
		}
	}
	
	/**
	 * Registers on the network
	 * 
	 * @param nick The nickname to use
	 * @throws IOException When registration fails
	 */
	private void register(String nick) throws IOException {
		if (!serverInfo.serverPass.isEmpty())
			send("PASS " + serverInfo.serverPass);
		send(IO.compile("NICK", new String[] { nick }, ""));
		send(IO.compile("USER", new String[] { serverInfo.username, "0", "*" },
				"MicroIRC Android client"));
	}

	/**
	 * Convert typical user input to a RAW IRC line
	 * Useful when making a client, pass user input directly to here
	 * 
	 * @param currentWindow Whether a user nick or channel, used to send PRIVMSGs in case {@code msg}
	 *                      isn't a command
	 * @param msg The user input
	 * @return The RAW IRC line, parsed from the user input
	 */
	public String parseUserInput(String currentWindow, String msg) {
		if (!msg.startsWith("/"))
			return IO.privmsg(currentWindow, msg);
		else {
			if (msg.startsWith("//"))
				return IO.privmsg(currentWindow, msg.substring(1));
			else {
				String[] splitter = msg.split(" ", 2);
				String cmd = splitter[0];
				String args = (splitter.length == 2 ? " " + splitter[1] : "");
				switch (cmd) {
				case "/cs":
				case "/chanserv":
					return "PRIVMSG CHANSERV :" + args;
				case "/ns":
				case "/nickserv":
					return "PRIVMSG NICKSERV :" + args;
				case "/privmsg":
				case "/msg":
					return "PRIVMSG" + args;
				case "/close":
				case "/part":
					return "PART"
							+ (args.equals("") ? " " + currentWindow : args);
				case "/quote":
				case "/raw":
					return msg;
				case "/discon":
				case "/disconnect":
				case "/bye":
				case "/quit":
					return "QUIT :" + args;
				case "/ping":
					if (finishedUserLagMeasurement) {
						userLagStart = System.currentTimeMillis();
						finishedUserLagMeasurement = false;
						return "PING :" + userLagPrefix + (userLagPingId = idGen.incrementAndGet());
					} else return "";
				default:
					return cmd.substring(1).toUpperCase() + args;
				}
			}
		}
	}
	
	/**
	 * Sets an event handler.
	 * In most, if not all, cases you'd create your own implementation of
	 * {@code IEventHandler}, to handle events fired
	 * 
	 * @param handler The events handler
	 */
	public void setEventHandler(IEventHandler handler) {
		this.eventHandler = handler;
	}
	
	/**
	 * Checks if server supports a certain capability
	 * 
	 * @param capability The capability to check for
	 * @return true if server supports it, otherwise false
	 */
	public boolean hasCapability(String capability) {
		for (String c : ircv3Capabilities)
			if (c.equals(capability))
				return true;
		return false;
	}

	/**
	 * Gets the IRCv3 capabilities supported by the IRC server
	 * 
	 * @return String array containing supported capabilities
	 */
	public String[] getIrcv3Capabilities() {
		return ircv3Capabilities;
	}
	
	/**
	 * Preserves channels after leaving them.
	 * Default value is to remove channels when leaving them
	 * 
	 * @param value The value
	 */
	public void setPreserveChannels(boolean value) {
		preserveChannels = value;
	}

	/**
	 * Sets the maximum amount of channel messages.
	 * For example, if {@code value} was 50, then only the last 50 messages
	 * of the channel are kept, older ones are removed
	 * 
	 * @param value Channel buffer length
	 */
	public void setChannelBufferLength(int value) {
		Channel.bufferLength = value;
	}
	
	/**
	 * Sets the maximum amount of user messages.
	 * For example, if {@code value} was 50, then only the last 50 messages
	 * of the user are kept, older ones are removed
	 * 
	 * @param value User buffer length
	 */
	public void setUserBufferLength(int value) {
		User.bufferLength = value;
	}
	
	/**
	 * Checks if this server supports IRCv3.
	 * 
	 * @return true if supports IRCv3, otherwise false
	 */
	public boolean isSupportsIrcv3() {
		return ircv3Support;
	}

	/**
	 * Get the server name
	 * This is the server name parsed from 005
	 * 
	 * @return Empty String if not available, otherwise the server name
	 */
	public String getServerName() {
		return serverName;
	}

	/**
	 * Gets the Message of The Day.
	 * There's an event that's fired after receiving a MOTD
	 * 
	 * @return Empty String if not available yet, otherwise the actual MOTD
	 */
	public String getMotd() {
		return motd.toString().substring(1);
	}
	
	/**
	 * Gets server information as {@code ServerInfo}.
	 * 
	 * @return The same server informations passed to the constructor
	 */
	public ServerInfo getServerInfo() {
		return serverInfo;
	}
	
	/**
	 * Queue {@code data} to be flushed to the server.
	 * 
	 * @param data
	 * @return Message ID, -1 if {@code data} is invalid
	 */
	public int send(String data) {
		if (data == null || data.equals(""))
			return -1;
		Message msg = new Message(data);
		try {
			writingQueue.put(msg);
			return msg.getId();
		} catch (Exception e) {
			eventHandler.onEvent(Event.DATA_SEND_FAIL, new Message(data));
		}
		return -1;
	}
	
	/**
	 * Gets a channel object by name.
	 * 
	 * @param channelName The name of the channel
	 * @return null if channel doesn't exist
	 */
	public Channel getChannel(String channelName) {
		return chans.get(channelName);
	}
	
	/**
	 * Get a {@code Collection} of all the channels.
	 * If {@code preserveChannels} is true, then this returns also left channels
	 * You can check if the channel has been left by the hasLeft() method in the channel
	 * 
	 * @return Collection of all the channels
	 */
	public Collection<Channel> getAllChannels() {
		return chans.values();
	}
	
	/**
	 * Disconnects from IRC server.
	 * 
	 * @param quitMsg The quit message
	 */
	public void disconnect(String quitMsg) {
		send("QUIT :" + quitMsg);
	}

	/**
	 * Gets current server lag.
	 * If lag measurement hasn't finished yet, it returns the current time difference
	 * 
	 * @return Server lag in milliseconds
	 */
	public long getLag() {
		return finishedLagMeasurement ? lag : System.currentTimeMillis()
				- lagStart;
	}
	
	/**
	 * Enables throttling to prevent flooding
	 * Default is true
	 * 
	 * @param value true to throttle, false to not
	 */
	public void setThorrlingEnabled(boolean value) {
		io.throttlingEnabled = true;
	}
	
	class LagPing extends TimerTask {
		@Override
		public void run() {
			if (!finishedLagMeasurement)
				return;
			lagStart = System.currentTimeMillis();
			finishedLagMeasurement = false;
			send("PING :" + lagPrefix + (lagPingId = idGen.incrementAndGet()));
		}
	}
}