package server;

/**
 * Created by Luca Tartarini on 12/11/14.
 */

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.HashSet;

/**
 * A multithreaded secure chat room server. All messages from a client will be broadcast to all other clients in the
 * chat room
 */

/** Server keypair generation
 *
 * keytool -genkeypair -alias chiaveServer -validity 365 -keystore /home/luca/IdeaProjects/JavaSecureChat/src/server/serverKeystore.jks -keyalg RSA
 *
 * Immettere la password del keystore:                                        server
 * Immettere nuovamente la password:			                              server
 * Specificare nome e cognome:                                                Server
 * Specificare il nome dell'unita aziendale:                                  Unibo
 * Specificare il nome dell'azienda:                                          Unibo
 * Specificare la localita:                                                   Bologna
 * Specificare la provincia                                                   BO
 * Specificare il codice a due lettere del paese in cui si trova l'unita:     IT
 * Il dato CN=server, OU=unibo, O=unibo, L=Rimini, ST=RN, C=IT e corretto?    si
 * Immettere la password della chiave per <chiaveServer>:                     ENTER
 *
 * Export the certificate of the public key in a file
 *
 * keytool -exportcert -alias chiaveServer -keystore /home/luca/IdeaProjects/JavaSecureChat/src/server/serverKeystore.jks -file /home/luca/IdeaProjects/JavaSecureChat/src/server/server.cer
 *
 * Import the client certificate
 *
 * keytool -importcert -alias chiaveClient -file /home/luca/IdeaProjects/JavaSecureChat/src/client/client.cer -keystore /home/luca/IdeaProjects/JavaSecureChat/src/server/serverTruststore.jks
 *
 * Immettere la password del keystore:              server
 * Immettere nuovamente la nuova password:          server
 * Stampa dei dati...
 * Considerare attendibile questo certificato?      si
 * Il certificato e stato aggiunto al keystore
 */

public class ChatServer {

    // Port that the server listens on.
    private static final int PORT = 9001;

    // Set of all names of clients in the chat room.
    private static HashSet<String> names = new HashSet<String>();

    // Set of all the print writers for all the clients.
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();

    // Application main method, which just listens on a port and runs handler threads.
    public static void main(String[] args) throws Exception {

        System.out.println("The chat server is running.");

        // Initialization ServerSocketFactory to ensure authentication and confidentiality
        char[] passphrase = "server".toCharArray();
        // The KeyStore file name
        String keyStoreFileName = "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverKeystore.jks";
        // TrustStore file name
        String keyStoreTrustFileName = "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverTruststore.jks";
        /*
         * SSLContext: secure socket protocol implementation which acts as a factory for secure socket factories
         * protocol: TLS
         */
        SSLContext sslContext = SSLContext.getInstance("TLS");
        /*
         * Factory for key managers, each key manager manages a specific type of key material for use by secure sockets.
         * The key material is based on a KeyStore and/or provider specific sources
         */
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SUNX509");
        // KeyStore: storage facility for cryptographic keys and certificates
        KeyStore keyStore = KeyStore.getInstance("JKS");
        // Loads this KeyStore from the given input stream
        keyStore.load(new FileInputStream(keyStoreFileName), passphrase);
        // Initializes this factory with a source of key material
        keyManagerFactory.init(keyStore, passphrase);
        // TrustManagers decide whether to allow connections
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
        KeyStore keyStoreTrust = KeyStore.getInstance("JKS");
        keyStoreTrust.load(new FileInputStream(keyStoreTrustFileName), passphrase);
        trustManagerFactory.init(keyStoreTrust);
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        ServerSocketFactory factory = sslContext.getServerSocketFactory();
        SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(PORT);
        // Server KeyStore file
        System.setProperty("javax.net.ssl.keyStore", "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverKeystore.jks");
        // Server KeyStore password
        System.setProperty("javax.net.ssl.keyStorePassword", "server");
        // Server TrustStore file
        System.setProperty("javax.net.ssl.trustStore", "/home/luca/IdeaProjects/JavaSecureChat/src/server/serverTruststore.jks");
        // Server TrustStore password
        System.setProperty("javax.net.ssl.trustStorePassword", "server");
        // Mutual identification, also by the client
        sslServerSocket.setNeedClientAuth(true);
        sslServerSocket.setWantClientAuth(true);
        try {
            while (true) {
                new Handler((SSLSocket) sslServerSocket.accept()).start();
                System.out.println("Connection request accepted");
            }
        } finally {
            sslServerSocket.close();
        }
    }

    // Handler threads are spawned from the listening loop.
    private static class Handler extends Thread {

        private String name;
        private SSLSocket socket;
        private BufferedReader in;
        private PrintWriter out;

        public Handler(SSLSocket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Create streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                // Request a name from this client. Keep requesting until a name is submitted that is not already used
                while (true) {
                    out.println("SUBMIT_NAME");
                    out.flush();
                    name = in.readLine();
                    if (name == null)
                        return;
                    // Checking for the existence of a name and adding the name must be done while locking the set of names
                    synchronized (names) {
                        if (!names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }
                // Add the socket's print writer to the set of all writers so this client can receive broadcast messages
                out.println("NAME_ACCEPTED");
                out.flush();
                writers.add(out);
                for (PrintWriter writer : writers) {
                    writer.println("NEW_USER " + name);
                    // List of users
                    writer.println("USERLIST_BEGIN");
                    for (String username : names)
                        writer.println(username);
                    writer.println("USERLIST_END");
                    writer.flush();
                }
                // Accept messages from this client and broadcast them
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
                // The client is going down
                out.println("EXIT");
                out.flush();
                // Remove client's name, its print writer from the sets and close its socket
                if (name != null)
                    names.remove(name);
                if (out != null)
                    writers.remove(out);
                // Remove the users from each client's list
                for (PrintWriter writer : writers) {
                    writer.println("REMOVE_USER " + name);
                    writer.println("USERLIST_START");
                    for (String username : names)
                        writer.println(username);
                    writer.println("USERLIST_STOP");
                    writer.flush();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Client closed");
            }
        }
    }
}