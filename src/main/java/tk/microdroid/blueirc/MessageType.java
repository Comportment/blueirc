package tk.microdroid.blueirc;

/**
 * The type of the parsed IRC command received from server.
 * This is used in {@code Parser}.
 * OTHER is something like a PING or QUIT, while UNKNOWN cannot be parsed.
 *
 */
public enum MessageType {
	NUMERIC, ACTION, OTHER, UNKNOWN
}
