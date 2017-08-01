package tk.microdroid.blueirc;

public class Example {
	static Worker worker;
	
	public static void main(String[] args) {
		worker = new Worker("irc.subluminal.net", 6667, "BlueIRCNick", "BlueIRCNick_",
				"BlueIRCUser", "", "", false, false);
		worker.setEventHandler(new MyHandler());
		worker.setChannelBufferLength(50);
		worker.setUserBufferLength(50);
		System.out.print("Connecting...");
		worker.start();
	}
	
	static class MyHandler implements IEventHandler {
		@Override
		public void onEvent(Event event, Object args) {
			switch (event) {
			case CONNECTED:
				System.out.println("Connected to: " + args); // Remember to check the reference for
				                                                    // args type
				if (worker.isSupportsIrcv3()) {
					StringBuilder sb = new StringBuilder();
					for (String capability : worker.getIrcv3Capabilities())
						sb.append(capability + " ");
					System.out.println("This server supports IRCv3 with the following capabilities: "
						+ sb.toString().trim());
				}
				System.out.println("Joining #blueirc..");
				worker.send("JOIN #blueirc");
				break;
			case UNKNOWN_HOST:
			case UNKNOWN_ERROR:
			case TIMEOUT:
				System.out.println("Whoops! crashed! " + event);
				break;
			case DISCONNECTED:
				System.out.println("Disconnected from " + (String)args);
				break;
			case GOT_SERVER_NAME:
				System.out.println("Server name is: " + (String)args);
				break;
			case GOT_MOTD:
				System.out.println("Got motd!");
				//System.out.println((String) args);
				break;
			case JOINED_CHANNEL:
				System.out.println("Joined " + (String) args);
				Channel channel = worker.getChannel((String) args);
				System.out.println("Topic for " + (String)args + ": " + channel.getTopic());
				StringBuilder sb = new StringBuilder();
				for (String userNick : channel.getUsers().keySet())
					sb.append(userNick +  " ");
				System.out.println("Users of #blueirc: " + sb.toString().trim());
				break;
			case DATA_RECEIVED: // You handle ACTION-based events (Like when receiving an INVITE) here
				Parser p = (Parser)args;
				switch (p.getAction()) {
				case "INVITE":
					worker.send("JOIN " + p.getMsg());
					break;
				case "PRIVMSG":
					if (p.getActionArgs().get(0).equals(worker.getServerInfo().nick)) // i.e. PRIVMSGed us
						// IO.compile generates an IRC command depending on ACTION, ARGUMENTS[], and MSG
						// Where MSG is the part that comes after ' :'
						worker.send(IO.compile("PRIVMSG", new String[] {p.getNick()}, "I am a bot, idk how to respond"));
					break;
				}
				break;
			case LAG_MEASURED:
				System.out.println("Current server lag: " + (long)args + "ms");
				break;
			default:
				break;
			}
		}
	}
}
