/**
 * A communication channel between an IMAP server that only accepts secure connections, 
 * and a client that does not support emails over secure connections.
 */

package secureemailproxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class ProxyChannel implements Runnable
{
	private static final String NEW_LINE = "\r\n";
	private static final String PROTOCOL = "TLSv1";
	private static final String IMAP_HOST = "localhost";
	private static final int IMAP_PORT = 993;
	
	private Socket plainSocket = null;
	private BufferedReader plainBufferedReader = null;
	private OutputStream plainOutputStream = null;

	private static SSLSocketFactory sslSocketFactory = null;
	private SSLSocket sslSocket = null;
	private SSLSession sslSession = null;
	private BufferedReader sslBufferedReader = null;
	private OutputStream sslOutputStream = null;
	
	// Initialize ssl socket factory
	static
	{
		try {
			SSLContext sslContext = SSLContext.getInstance( PROTOCOL);
			sslContext.init( null, new X509TrustManager[]{ new CustomTrustManager() }, null);
			sslSocketFactory = sslContext.getSocketFactory();
		} catch( NoSuchAlgorithmException e) {
			Proxy.log( Level.SEVERE, e.toString(), e);
		} catch( KeyManagementException e) {
			Proxy.log( Level.SEVERE, e.toString(), e);
		}
	}
	
	public ProxyChannel( Socket plainSocket)
	{
		this.plainSocket = plainSocket;
	}
	
	@Override
	public void run()
	{
		try {
			Proxy.log( Level.INFO, "Will try to establish a secure connection to " + IMAP_HOST + ":" + IMAP_PORT);
			
			// Establish a secure connection to the email server
			sslSocket = (SSLSocket) sslSocketFactory.createSocket( IMAP_HOST, IMAP_PORT);
			sslSession = sslSocket.getSession();

			String[] protocols = sslSocket.getEnabledProtocols();
			for( int i = 0; i < protocols.length; i++)
				Proxy.log( Level.FINER, protocols[i]);

			Certificate[] certificateChain = sslSession.getPeerCertificates();

			Proxy.log( Level.FINER, "The certificates used by peer");
			for( int i = 0; i < certificateChain.length; i++)
				Proxy.log( Level.FINER, ((X509Certificate) certificateChain[i]).getSubjectDN().toString());
			
			Proxy.log( Level.INFO, "Established a secure connection to " + IMAP_HOST + ":" + IMAP_PORT);
			
			// Create input and output streams between proxy and server
			sslBufferedReader = new BufferedReader( new InputStreamReader( sslSocket.getInputStream()));
			sslOutputStream = sslSocket.getOutputStream();

			Proxy.log( Level.INFO, "Starting to listen");

			// Create input and output streams between proxy and client
			plainBufferedReader = new BufferedReader( new InputStreamReader( plainSocket.getInputStream()));
			plainOutputStream = plainSocket.getOutputStream();
			
			Thread[] threads = new Thread[2];
			
			// Create a thread for listening to messages from server and forwarding them to client
			threads[0] = new Thread( new Runnable()
				{
					public void run()
					{
						String sslInputLine = null;
						try {
							// Listen to messages from server
							while(( sslInputLine = sslBufferedReader.readLine()) != null)
							{
								// readLine loses line separator, re-add it.
								sslInputLine = sslInputLine + NEW_LINE;
								plainOutputStream.write( sslInputLine.getBytes());
								Proxy.log( Level.FINEST, "From server to client: " + sslInputLine);
							}
						} catch( IOException e) {
							Proxy.log( Level.SEVERE, e.toString(), e);
						}
					}
				}
			);

			// Create a thread for listening to messages from client and forwarding them to server
			threads[1] = new Thread( new Runnable()
				{
					public void run()
					{
						String plainInputLine = null;
						try {
							// Listen to messages from server
							while(( plainInputLine = plainBufferedReader.readLine()) != null)
							{
								// readLine loses line separator, re-add it.
								plainInputLine = plainInputLine + NEW_LINE;
								sslOutputStream.write( plainInputLine.getBytes());
								Proxy.log( Level.FINEST, "From client to server: " + plainInputLine);
							}
						} catch( IOException e) {
							Proxy.log( Level.SEVERE, e.toString(), e);
						}
					}
				}
			);
			
			// Start both threads
			for( Thread thread : threads)
				thread.start();
			
			// Wait until both threads end
			for( Thread thread : threads)
				thread.join();
		} catch( IOException e) {
			Proxy.log( Level.SEVERE, e.toString(), e);
		} catch( InterruptedException e) {
			Proxy.log( Level.SEVERE, e.toString(), e);
		} finally {
			try {
				if( sslSocket != null)
					sslSocket.close();
			} catch( IOException e) {
				Proxy.log( Level.SEVERE, e.toString(), e);
			}
			
			try {
				if( plainSocket != null)
					plainSocket.close();
			} catch( IOException e) {
				Proxy.log( Level.SEVERE, e.toString(), e);
			}
		}
	}
}
