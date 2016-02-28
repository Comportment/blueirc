package tk.microdroid.blueirc;

import java.io.IOException;

public class Handler {
	static void handle(Worker w, Parser p) throws IOException {
		switch (p.action) {
		case "PING":
			w.send("PONG" + w.io.line.substring(4));
			break;
		case "PONG": // For lag measurement
			if (p.msg.equals(w.lagPrefix + w.lagPingId)) {
				w.finishedLagMeasurement = true;
				w.lag = System.currentTimeMillis() - w.lagStart;
				w.eventHandler.onEvent(Event.LAG_MEASURED, w.lag);
			} else if (p.msg.equals(w.userLagPrefix + w.userLagPingId)) {
				w.finishedUserLagMeasurement = true;
				w.eventHandler.onEvent(Event.USER_LAG_MEASURED, System.currentTimeMillis() - w.userLagStart);
			}
		case "PRIVMSG": // Add the message to the chan/user
			if (p.actionArgs.get(0).matches("[\\#\\&].+")) {
				Channel chan = w.chans.get(p.actionArgs.get(0));
				chan.getUsers().get(p.nick).addMessage(p);
				chan.addMessage(p);
			} else {
				if (w.users.containsKey(p.actionArgs.get(0))) {
					w.users.get(p.actionArgs.get(0)).addMessage(p);
				} else {
					User user = new User(p.actionArgs.get(0), "");
					user.addMessage(p);
				}
			}
			break;
		case "CAP":
			w.ircv3Support = true;
			String capType = p.actionArgs.get(1);
			if (capType.equals("LS")) {
				w.ircv3Capabilities = p.msg.split(" ");
				String[] reqCpbs = { "multi-prefix" }; // Requested capabilities
				for (String reqCpb : reqCpbs)
					if (w.hasCapability(reqCpb))
						w.send("CAP REQ " + reqCpb);
				w.send("CAP END");
				w.register(w.serverInfo.nick);
			} else if (capType.equals("NAK"))
				w.eventHandler.onEvent(
						Event.IRCV3_CAPABILITY_REJECTED, p.msg);
			else if (capType.equals("ACK"))
				w.eventHandler.onEvent(
						Event.IRCV3_CAPABILITY_ACCEPTED, p.msg);
			break;
		case "JOIN": // Create new Channel
			if (p.nick.equals(w.usingSecondNick ? w.serverInfo.secondNick : w.serverInfo.nick)) {
				if (!w.chans.containsKey(p.msg)) {
					w.chans.put(p.msg, new Channel(p.msg));
				}
				else {
					w.chans.get(p.msg).rejoin();
				}
			} else {
				if (w.chans.containsKey(p.msg)) {
					Channel chan = w.chans.get(p.msg);
					if (!chan.hasUser(p.nick))
						chan.addUser(p.nick, null);
				}
			}
			break;
		case "PART": // Remove channel (When not preserving them) or user in channel
			if (p.nick
					.equals(w.usingSecondNick ? w.serverInfo.secondNick
							: w.serverInfo.nick)) {
				w.eventHandler.onEvent(Event.LEFT_CHANNEL,
						p.actionArgs.get(0));
				w.chans.get(p.actionArgs.get(0)).leave();
				if (!w.preserveChannels)
					w.chans.remove(p.actionArgs.get(0));
			} else {
				w.chans.get(p.actionArgs.get(0)).removeUser(p.nick);
				if (!w.preserveUsers) {
					boolean userStillVisible = false; // i.e. We can see the user somewhere in the other channels
					for (Channel chan : w.chans.values())
						userStillVisible = chan.hasUser(p.nick) || userStillVisible;
					if (!userStillVisible)
						w.users.remove(p.nick);
				}
			}
			break;
		case "NICK": // Update nicknames upon nicknames change
			if (p.msg.equals(w.serverInfo.nick))
				w.serverInfo.nick = p.msg;
			for (Channel chan : w.chans.values())
				for (User user : chan.getUsers().values())
					if (user.getNick().equals(p.nick))
						user.updateNick(p.msg);
			if (w.users.containsKey(p.nick))
				w.users.get(p.nick).updateNick(p.msg);
			break;
		case "KICK": // Remove channel (If not preserving them) or user in channel
			if (p.actionArgs.get(1).equals(
					w.usingSecondNick ? w.serverInfo.secondNick
							: w.serverInfo.nick)) {
				w.eventHandler.onEvent(Event.KICKED, p);
				w.chans.get(p.actionArgs.get(0)).leave();
				if (!w.preserveChannels)
					w.chans.remove(p.actionArgs.get(0));
			} else {
				w.chans.get(p.actionArgs.get(0)).removeUser(
						p.actionArgs.get(1));
				if (!w.preserveUsers) {
					boolean userStillVisible = false; // i.e. We can see the user somewhere in the other channels
					for (Channel chan : w.chans.values())
						userStillVisible = chan.hasUser(p.nick) || userStillVisible;
					if (!userStillVisible)
						w.users.remove(p.nick);
				}
			}
		break;
		case "QUIT": // Remove user from all the channels
			if (!w.preserveUsers) {
				for (Channel chan : w.chans.values())
					chan.removeUser(p.nick);
				if (w.users.containsKey(p.nick))
					w.users.remove(p.nick);
			}
		case "TOPIC": // Update topic upon change
			if (w.chans.containsKey(p.actionArgs.get(0)))
					w.chans.get(p.actionArgs.get(0)).setTopic(p.msg);
			break;
		case "332": // The topic sent upon join
			if (w.chans.containsKey(p.actionArgs.get(1)))
				w.chans.get(p.actionArgs.get(1)).setTopic(p.msg);
		case "001": // Welcome to the server
			w.eventHandler.onEvent(Event.CONNECTED, p.server);
			w.lagTimer.schedule(w.new LagPing(), 0, 30000);
		case "421": // Unknown command CAP (i.e. the server doesn't support IRCv3)
			if (p.actionArgs.get(1).equals("CAP"))
				w.register(w.serverInfo.nick);
		case "353": // NAMES response
			if (w.chans.containsKey(p.actionArgs.get(2))) {
				Channel chan = w.chans.get(p.actionArgs.get(2));
				for (String user : p.msg.split(" ")) {
					chan.addUser(user, w.prefixes);
					if (!w.users.containsKey(user))
						w.users.put(user, new User(user, w.prefixes));
				}
			}
		case "005": // Server capabilities, sent upon connection
			for (String spec : p.actionArgs) {
				String[] kvSplitter = spec.split("=", 2);
				String key = kvSplitter[0].toUpperCase();
				String value = kvSplitter.length == 2 ? kvSplitter[1]
						: "";
				switch (key) {
				case "PREFIX":
					String[] splitter = value.substring(1).split("\\)");
					for (int i = 0; i < splitter[0].length(); i++) {
						w.prefixes.put(splitter[1].charAt(i),
								splitter[0].charAt(i));
					}
					break;
				case "NETWORK":
					w.serverName = value;
					w.eventHandler.onEvent(Event.GOT_SERVER_NAME,
							value);
				}
			}
		case "375": // Start of MOTD
			w.motd = new StringBuilder();
		case "372": // MOTD message
			w.motd.append("\n" + p.msg);
		case "376": // End of MOTD
			w.motd.trimToSize();
			w.eventHandler.onEvent(Event.GOT_MOTD, w.motd.toString()
					.substring(1));
		case "366": // Channel joined
			w.eventHandler.onEvent(Event.JOINED_CHANNEL, p.actionArgs.get(1));
		case "433": // Nickname in use
			if (!w.usingSecondNick) {
				w.eventHandler.onEvent(Event.FIRST_NICK_IN_USE,
						w.serverInfo.nick);
				w.register(w.serverInfo.secondNick);
			} else {
				w.eventHandler.onEvent(Event.ALL_NICKS_IN_USE,
						w.serverInfo.secondNick);
				break;
			}
		}
	}
}
