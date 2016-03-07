package tk.microdroid.blueirc;

import tk.microdroid.blueirc.Channel;
import tk.microdroid.blueirc.Event;
import tk.microdroid.blueirc.IEventHandler;
import tk.microdroid.blueirc.IO;
import tk.microdroid.blueirc.Parser;
import tk.microdroid.blueirc.Worker;

public class Example {
	static Worker worker;
	
	public static void main(String[] args) {
		System.setProperty("socksProxyHost","localhost");
		System.setProperty("socksProxyPort","23456");
		
		worker = new Worker("irc.freenode.net", 6697, "BlueIRCNick", "BlueIRCNick_",
				"BlueIRCUser", true, true);
		worker.setEventHandler(new MyHandler());
		worker.setChannelBufferLength(50);
		worker.setUserBufferLength(50);
		System.out.print("Connecting.. ");
		worker.start();
	}
	
	static class MyHandler implements IEventHandler {
		@Override
		public void onEvent(Event event, Object args) {
			switch (event) {
			case CONNECTED:
				System.out.println("Connected to " + (String)args); // Remember to check the reference for
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
				//defaults to true. This module determines if a WHO command will be sent or not on JOIN
				if(worker.getWHOSetting()==true){
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					worker.send("WHO #blueirc");
				}
				
				break;
			case UNKNOWN_HOST:
			case UNKNOWN_ERROR:
			case TIMEOUT:
				System.out.println("Whoops! crashed! " + ((Exception)args).getMessage());
				break;
			case SSL_CERTIFICATE_REFUSED:
				System.out.println("Server uses invalid/self-signed SSL certificate!");
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
				switch (p.action) {
				case "INVITE":
					worker.send("JOIN " + p.msg);
					break;
				case "PRIVMSG":
					if (p.actionArgs.get(0).equals(worker.getServerInfo().nick)) // i.e. PRIVMSGed us
						// IO.compile generates an IRC command depending on ACTION, ARGUMENTS[], and MSG
						// Where MSG is the part that comes after ' :'
						worker.send(IO.compile("PRIVMSG", new String[] {p.nick}, "I am a bot, idk how to respond"));
					break;
				}
				break;
			case LAG_MEASURED:
				System.out.println("Current server lag: " + (long)args + "ms");
				break;
			case CERTIFICATE_PINNING_START: // Only when you accept invalid cert. on a server with self-signed one
				System.out.print("Pinning certificate.. ");
				worker.onCertificatePinning(true);
				break;
			default:
				break;
			}
		}
	}
}
