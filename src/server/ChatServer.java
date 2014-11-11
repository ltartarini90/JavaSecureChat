package server;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.HashSet;

/**
 * A multithreaded chat room server. When a client connects the server requests a screen name by sending the client the
 * text "SUBMITNAME", and keeps requesting a name until a unique one is received. After a client submits a unique
 * name, the server acknowledges with "NAMEACCEPTED". Then all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name. The broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple chat server, there are a few features that have been
 * left out. Two are very useful and belong in production code:
 *
 *     1. The protocol should be enhanced so that the client can
 *        send clean disconnect messages to the server.
 *
 *     2. The server should do some logging.
 */

public class ChatServer {

    /**
     * The port that the server listens on.
     */
    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room.  Maintained
     * so that we can check that new clients are not registering name
     * already in use.
     */
    private static HashSet<String> names = new HashSet<String>();

    /**
     * The set of all the print writers for all the clients.  This
     * set is kept so we can easily broadcast messages.
     */
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();

    /**
     * The appplication main method, which just listens on a port and
     * spawns handler threads.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");

        // Initialization ServerSocketFactory to ensure authentication and confidentiality
        char[] passphrase = "server".toCharArray();
        String keyStoreFileName = "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverKeystore.jks";
        String keyStoreTrustFileName = "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverTruststore.jks";
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SUNX509");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keyStoreFileName), passphrase);
        keyManagerFactory.init(keyStore, passphrase);
        // TrustManagers decide whether to allow connections
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
        KeyStore keyStoreTrust = KeyStore.getInstance("JKS");
        keyStoreTrust.load(new FileInputStream(keyStoreTrustFileName), passphrase);
        trustManagerFactory.init(keyStoreTrust);
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        ServerSocketFactory factory = sslContext.getServerSocketFactory();

        SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(PORT);
        System.setProperty("javax.net.ssl.keyStore", "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverKeystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "server");

        // Mutual identification
        sslServerSocket.setNeedClientAuth(true);
        sslServerSocket.setWantClientAuth(true); // Also by the customer
        try {
            while (true) {
                new Handler((SSLSocket) sslServerSocket.accept()).start();
                System.out.println("Connection request received...");
            }
        } finally {
            sslServerSocket.close();
        }
    }

    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for a dealing with a single client
     * and broadcasting its messages.
     */
    private static class Handler extends Thread {
        private String name;
        private SSLSocket socket;
        private BufferedReader in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(SSLSocket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMIT_NAME");
                    out.flush();
                    name = in.readLine();
                    if (name == null)
                        return;
                    synchronized (names) {
                        if (!names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the
                // socket's print writer to the set of all writers so
                // this client can receive broadcast messages.
                out.println("NAME_ACCEPTED");
                out.flush();
                writers.add(out);
                for (PrintWriter writer : writers) {
                    writer.println("NEW_USER " + name);
                    writer.println("USERLIST_BEGIN");
                    for (String username : names)
                        writer.println(username);
                    writer.println("USERLIST_END");
                    writer.flush();
                }

                // Accept messages from this client and broadcast them.
                // Ignore other clients that cannot be broadcasted to.
                while (true) {
                    String input = in.readLine();
                    if (input.equals("EXIT"))
                        break;
                    else if (input == null)
                        return;
                    for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + name + ": " + input);
                        writer.flush();
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                out.println("EXIT");
                out.flush();
                // This client is going down!  Remove its name and its print
                // writer from the sets, and close its socket.
                if (name != null)
                    names.remove(name);
                if (out != null)
                    writers.remove(out);
                for (PrintWriter writer : writers) {
                    writer.println("REMOVE_USER " + name);
                    writer.println("USERLIST_START");
                    for (String username : names)
                        writer.println(username);
                    writer.println("USERLIST_STOP");
                    writer.flush();
                }
                System.out.println("Client closed");
            }
        }
    }
}