package tk.microdroid.blueirc;

/**
 * Simply, an event handler for {@code Event}s.
 * Whenever one of the {@code Event}s occur, onEvent is fired, with {@code event}
 * As the {@code Event}, and {@code args}, as the event arguments,
 * {@code args}'s type can be different depending on {@code event}
 * 
 * See the reference below, it's also on the wiki on Github
 * 
 * @see Event
 * 
 */
public interface IEventHandler {
    public void onEvent(Event event, Object args);
    
    /*
     * event                     -> typeof(args)           -> Description
     * 
     * ALL_NICKS_IN_USE          -> String                 -> The second nick
     * CERTIFICATE_PINNING_FAIL  -> String                 -> Reason
     * CERTIFICATE_PINNING_START -> String                 -> Informations with public key
     * CONNECTED                 -> String                 -> Server URL
     * DISCONNECTED              -> String                 -> Server URL
     * FIRST_NICK_IN_USE         -> String                 -> The first nick
     * GOT_SERVER_NAME           -> String                 -> The server name
     * GOT_MOTD                  -> String                 -> The new motd
     * SSL_CERTIFICATE_REFUSED   -> IOException            -> The exception object
     * IRCV3_CAPABILITY_ACCEPTED -> String                 -> The accepted capabilities
     * IRCV3_CAPABILITY_REJECTED -> String                 -> The rejected capabilities
     * KICKED                    -> Parser                 -> The parser for that, to also get the user kicked us
     * JOINED_CHANNEL            -> String                 -> The channel has been joined
     * LAG_MEASURED              -> long                   -> Lag in milliseconds
     * LEFT_CHANNEL              -> String                 -> The left channel
     * TIMEOUT                   -> SocketTimeoutException -> The exception object
     * UNKNOWN_ERROR             -> IOException            -> The exception object
     * UNKNOWN_HOST              -> UnknownHostException   -> The exception object
     * USER_LAG_MEASURED         -> long                   -> Lag in milliseconds
     * DATA_RECEIVED             -> Parser                 -> Parser object containing everything
     * DATA_SEND_FAIL            -> Message                -> The unsent message with it's ID
     * DATA_SENT                 -> Message                -> The sent message with it's ID
     */
}
