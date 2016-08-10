/**
 * A proxy that can be located between an IMAP server that only accepts secure connections, 
 * and a client that does not support emails over secure connections.
 */

package secureemailproxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Proxy
{
	private static final Logger LOGGER = Logger.getLogger( Proxy.class.getName());
	private static final String LOG_FILE_PATH = "ProxyLogger.log";
	private static final int PORT = 143;
	
	// Add a file handler to logger
	static
	{
		try {
			FileHandler fh = new FileHandler( LOG_FILE_PATH, true);
			LOGGER.addHandler( fh);
		} catch( SecurityException e) {
			e.printStackTrace();
		} catch( IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main( String[] args)
	{
		ServerSocket serverSocket = null;
		
		try {
			serverSocket = new ServerSocket( PORT);
			Proxy.log( Level.INFO, "Started listening on port " + PORT + " for plain text IMAP.");
			
			// While true, listen for connections
			while( true)
			{
				Socket plainSocket = serverSocket.accept();
				plainSocket.setKeepAlive( true);
				ProxyChannel proxyChannel = new ProxyChannel( plainSocket);
				
				// Each connection is handled in a separate thread
				Proxy.log( Level.INFO, "Received connection, opening new Thread");
				new Thread( proxyChannel).start();
			}
		} catch( IOException e) {
			Proxy.log( Level.SEVERE, e.toString(), e);
		} finally {
			try {
				if( serverSocket != null)
					serverSocket.close();
			} catch( IOException e) {
				Proxy.log( Level.SEVERE, e.toString(), e);
			}
		}
	}
	
	public static void log( Level level, String message)
	{
		LOGGER.log( level, message);
	}
	
	public static void log( Level level, String message, Throwable thrown)
	{
		LOGGER.log( level, message, thrown);
	}
}
