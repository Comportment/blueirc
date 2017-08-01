package tk.microdroid.blueirc;

/**
 * Contains connection info about the IRC server.
 * Either passed to the constructor of {@code Worker} or the other constructor creates it.
 * Either way, each Worker has an instance of this class 
 * 
 */
public class ServerInfo {
    public String server, nick, secondNick, username, nickservPass, serverPass;
    public int port;
    public boolean ssl, invalidSSL;
}
