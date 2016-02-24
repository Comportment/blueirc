package tk.microdroid.blueirc;

/**
 * Events are a way to notify the user of this library about various changes and updates
 * Events may NOT be INVITE or PRIVMSG, or whatever that can be parsed through the
 * Arguments of DATA_RECEIVED.
 * 
 * @see IEventHandler
 * 
 */
public enum Event {
    CONNECTED, DISCONNECTED, TIMEOUT, UNKNOWN_HOST, UNKNOWN_ERROR,
    DATA_SEND_FAIL, DATA_RECEIVED, DATA_SENT, IRCV3_CAPABILITY_REJECTED,
    IRCV3_CAPABILITY_ACCEPTED, JOINED_CHANNEL, LEFT_CHANNEL, GOT_SERVER_NAME,
    GOT_MOTD, KICKED, FIRST_NICK_IN_USE, ALL_NICKS_IN_USE, LAG_MEASURED
}
