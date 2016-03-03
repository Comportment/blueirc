package tk.microdroid.blueirc;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
	final static AtomicInteger idGen = new AtomicInteger(0);
	private int workerId = 0;

	Worker thisWorker;
	ServerInfo serverInfo;
	Thread writerThread;
	Thread mainThread;
	IO io;
	
	Object certPinningLock = new Object();
	boolean pinCertificate = true;
	volatile boolean certificateAccepted = false;
	
	IEventHandler eventHandler = new IEventHandler() {
		@Override
		public void onEvent(Event event, Object args) {
			// Nothing.
		}
	};
	Socket socket;
	SSLSocket sslSocket;
	BlockingQueue<Message> writingQueue = new ArrayBlockingQueue<Message>(
			32);
	boolean usingSecondNick = false;
	boolean connected = false;

	boolean ircv3Support = false;
	String[] ircv3Capabilities = {};
	HashMap<Character, Character> prefixes = new HashMap<>(); // Server prefixes, from 005
	String serverName = ""; // The server name received from 005
	StringBuilder motd = new StringBuilder();

	HashMap<String, Channel> chans = new HashMap<>();
	HashMap<String, User> users = new HashMap<>();
	boolean preserveChannels = false;
	boolean preserveUsers = false;

	long lag = 0;
	long lagStart = 0, userLagStart = 0;
	int lagPingId = 0, userLagPingId = 0;
	boolean finishedLagMeasurement = true, finishedUserLagMeasurement = true;
	Timer lagTimer = new Timer();
	final String lagPrefix = "blueirc.", userLagPrefix = "blueirc.user.";

	public Worker(String server, int port, String nick, String secondNick,
			String username, boolean ssl, boolean invalidSSL) {
		serverInfo = new ServerInfo();
		serverInfo.server = server;
		serverInfo.port = port;
		serverInfo.nick = nick;
		serverInfo.secondNick = secondNick;
		serverInfo.username = username;
		serverInfo.ssl = ssl;
		serverInfo.invalidSSL = invalidSSL;
		io = new IO();
		thisWorker = this;
	}

	public Worker(ServerInfo info) {
		serverInfo = info;
		io = new IO();
		thisWorker = this;
	}

	/**
	 * Start the {@code Worker} instance.
	 * 
	 * @return The unique worker id
	 */
	public int start() {
		mainThread = new Thread(new IRCWorkerRunnable());
		mainThread.start();
		return (workerId = idGen.incrementAndGet());
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
						sslSocket = (SSLSocket) sslFactory.createSocket(
								serverInfo.server, serverInfo.port);
						sslSocket.startHandshake();
						Certificate[] certs = sslSocket.getSession().getPeerCertificates();
						if (certs.length < 1)
							eventHandler.onEvent(Event.CERTIFICATE_PINNING_FAIL, "No certificates supplied!");
						else {
							if (pinCertificate) {
								eventHandler.onEvent(Event.CERTIFICATE_PINNING_START, certs[0].getPublicKey());
								synchronized (certPinningLock) {
									while (!certificateAccepted)
										certPinningLock.wait();
								}
							}
							if (sslSocket.isClosed())
								sslSocket = (SSLSocket) sslFactory.createSocket(serverInfo.server, serverInfo.port);
						}
					} else {
						sslFactory = (SSLSocketFactory) SSLSocketFactory
								.getDefault();
						sslSocket = (SSLSocket) sslFactory.createSocket(
								serverInfo.server, serverInfo.port);
					}
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
					Handler.handle(thisWorker, p);
				}
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				// Never's gonna happen
			} catch (UnknownHostException e) {
				eventHandler.onEvent(Event.UNKNOWN_HOST, e);
			} catch (SocketTimeoutException e) {
				eventHandler.onEvent(Event.TIMEOUT, e);
			} catch (IOException e) {
				if (e.getMessage().startsWith("sun.security.validator.ValidatorException"))
					eventHandler.onEvent(Event.SSL_CERTIFICATE_REFUSED, e);
				else 
					eventHandler.onEvent(Event.UNKNOWN_ERROR, e);
			} catch (Exception e) {
				eventHandler.onEvent(Event.UNKNOWN_ERROR, e);
			} finally {
				eventHandler.onEvent(Event.DISCONNECTED, serverInfo.server);
				lagTimer.cancel();
				connected = false;
				if (writerThread != null)
					writerThread.interrupt();
				try {
					if (serverInfo.ssl && sslSocket != null) sslSocket.close();
					else if (socket != null) socket.close();
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
	void register(String nick) throws IOException {
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
	 * Preserves users after them quitting or parting from known channels
	 * Default value is to remove users
	 * 
	 * @param value The value
	 */
	public void setPreserveUsers(boolean value) {
		preserveUsers = value;
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
	 * @return Server-sent name if available, if not, the server URL
	 */
	public String getServerName() {
		if (serverName == null)
			return serverInfo.server;
		return serverName.isEmpty() ? serverInfo.server : serverName;
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
	 * @param data The data to be flushed
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
	 * Returns users and channels at once.
	 * 
	 * Useful for making a client, use instanceof to check the type 
	 * of whether it's a user or a channel.
	 * 
	 * @return ArrayList of users and channels casted to Object
	 * 
	 */
	public ArrayList<Object> getAllConversations() {
		ArrayList<Object> result = new ArrayList<Object>();
		for (Channel chan : chans.values())
			result.add(chan);
		for (User user : users.values())
			result.add(user);
		return result;
	}
	
	/**
	 * Get a specific user object by nickname.
	 * 
	 * @param nick The nickname
	 * @return The User object
	 */
	public User getUser(String nick) {
		return users.get(nick);
	}
	
	/**
	 * Checks whether the user is ever known in any channel.
	 * Note that if the user has quit and {@code preserveUsers}
	 * Is false then the user is deleted and no longer known
	 * 
	 * @param nick The nickname
	 * @return True if the user is known, otherwise false
	 */
	public boolean hasUser(String nick) {
		return users.containsKey(nick);
	}
	
	/**
	 * Get a {@code Collection} of all the channels.
	 * If {@code preserveChannels} is true, then this returns also left channels
	 * You can check if the channel has been left by the hasLeft() method in the channel
	 * 
	 * @return Collection of all the channels
	 */
	public ArrayList<Channel> getAllChannels() {
		return new ArrayList<Channel>(chans.values());
	}
	
	/**
	 * Returns the known users from all the channels.
	 * 
	 * @return Collection of users
	 */
	public ArrayList<User> getAllUsers() {
		return new ArrayList<User>(users.values());
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
	public void setThrottlingEnabled(boolean value) {
		io.throttlingEnabled = true;
	}
	
	/**
	 * Set server password to use while connecting
	 * 
	 * @param password The password to use
	 */
	public void setServerPass(String password) {
		serverInfo.serverPass = password;
	}
	 /**
	  * Set whether to pin SSL certificate or not.
	  * Default is true.
	  * This is only valid if {@code serverInfo.invalidSSL} is true
	  * 
	  * @param value True to pin certificate, false otherwise
	  */
	public void setPinCertificate(boolean value) {
		pinCertificate = value;
	}
	
	/**
	 * Accept or deny the certificate.
	 * This is mandatory while doing certificate pinning, not calling this
	 * or not handling Event.CERTIFICATE_PINNING_START will prevent connection
	 * to the server, the server will probably time out the socket.
	 * 
	 * If the user took too long to accept the certificate and the socket timed
	 * out the the library automatically reconnects.
	 * 
	 * @param accepted True to accept the certificate and connect, false otherwise
	 */
	public void onCertificatePinning(boolean accepted) {
		try {
			certificateAccepted = true;
			certPinningLock.notifyAll();
			certPinningLock.notifyAll(); // For some reason I need to notify twice for it to work
			if (!accepted) {
				if (serverInfo.ssl)
					sslSocket.close();
				else 
					socket.close();
				lagTimer.cancel();
				writerThread.interrupt();
				mainThread.interrupt();
			}
		} catch (Exception e) {
			// Don't do anything
		}
	}
	
	/**
	 * Get the current worker unique ID
	 * 
	 * @return Current worker ID
	 */
	public int getId() {
		return workerId;
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