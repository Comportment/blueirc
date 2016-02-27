package tk.microdroid.blueirc;

/**
 * Contains connection info about the IRC server.
 * Either passed to the constructor of {@code Worker} or the other constructor creates it.
 * Either way, each Worker has an instance of this class 
 * 
 * @see Worker#Worker(ServerInfo)
 * 
 */
public class ServerInfo {
    public String server="", nick="", secondNick="", username="", nickservPass="", serverPass="";
    public int port=6667;
    public boolean ssl=false, invalidSSL=false;
}
