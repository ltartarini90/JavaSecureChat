package client;

/**
 * Created by Luca Tartarini on 12/11/14.
 */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.*;
import javax.swing.*;

/**
 * Client for the chat server
 */

/**
 * Client keypair generation:
 *
 * keytool -genkeypair -alias chiaveClient -validity 365 -keystore /home/luca/IdeaProjects/JavaSecureChat/src/client/clientKeystore.jks -keyalg RSA
 *
 * Immettere la password del keystore:                                        client
 * Immettere nuovamente la password:			                              client
 * Specificare nome e cognome:                                                Client
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
 * keytool -exportcert -alias chiaveClient -keystore /home/luca/IdeaProjects/JavaSecureChat/src/client/clientKeystore.jks -file /home/luca/IdeaProjects/JavaSecureChat/src/client/client.cer
 *
 * Import the client certificate
 *
 * keytool -importcert -alias chiaveServer -file /home/luca/IdeaProjects/JavaSecureChat/src/server/server.cer -keystore /home/luca/IdeaProjects/JavaSecureChat/src/client/clientTruststore.jks
 *
 * Immettere la password del keystore:              client
 * Immettere nuovamente la nuova password:          client
 * Stampa dei dati...
 * Considerare attendibile questo certificato?      si
 * Il certificato e stato aggiunto al keystore
 */

public class ChatClient {

    private static final int SERVER_PORT = 9001;

    SSLSocket socket;
    BufferedReader in;
    PrintWriter out;
    String serverAddress;
    // GUI components
    JFrame frame;
    JTextField textField;
    JTextArea messageArea;
    JTextArea usersArea;
    JButton exitButton;

    public ChatClient() {
        initGUI();
    }

    /**
     * Constructs the client by laying out the GUI and registering a
     * listener with the textfield so that pressing Return in the
     * listener sends the textfield contents to the server.  Note
     * however that the textfield is initially NOT editable, and
     * only becomes editable AFTER the client receives the NAMEACCEPTED
     * message from the server.
     */
    public void initGUI() {
        // Layout GUI
        frame = new JFrame("Client chat");
        textField = new JTextField("Type here...", 40);
        messageArea = new JTextArea(10, 40);
        usersArea = new JTextArea(10, 10);
        exitButton = new JButton("Exit");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        textField.setEditable(false);
        messageArea.setEditable(false);
        usersArea.setEditable(false);
        usersArea.append("Online users:\n");
        frame.getContentPane().add(textField, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.getContentPane().add(new JScrollPane(usersArea), BorderLayout.EAST);
        frame.getContentPane().add(exitButton, BorderLayout.PAGE_END);
        frame.pack();

        // Add Listeners
        textField.addActionListener(new ActionListener() {
            /**
             * Responds to pressing the enter key in the textfield by sending
             * the contents of the text field to the server.    Then clear
             * the text area in preparation for the next message.
             */
            public void actionPerformed(ActionEvent e) {
                out.println(textField.getText());
                out.flush();
                textField.setText("");
            }
        });
        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                textField.setText("");
            }
        });
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                super.focusGained(e);
                textField.setText("");
            }
        });
        exitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //Execute when button is pressed
                System.out.println("Exiting...");
                out.println("EXIT");
                out.flush();
            }
        });
    }

    // Prompt for and return the address of the server.
    private String getServerAddress() {
        return JOptionPane.showInputDialog(
                frame,
                "Enter IP address of the server:",
                "Welcome to the chat",
                JOptionPane.QUESTION_MESSAGE);
    }

    // Prompt for and return the desired screen name.
    private String getName() {
        return JOptionPane.showInputDialog(
                frame,
                "Choose username:",
                "Screen name selection",
                JOptionPane.PLAIN_MESSAGE);
    }

    // Connects to the server then enters the processing loop.
    private void run() throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException {

        serverAddress = getServerAddress();
        // Initialization ServerSocketFactory to ensure authentication and confidentiality
        char[] passphrase = "client".toCharArray();
        // The KeyStore file name
        String keyStoreFileName = "/home/luca/IdeaProjects/JavaSecureChat/src/client/clientKeystore.jks";
        // TrustStore file name
        String keyStoreTrustFileName = "/home/luca/IdeaProjects/JavaSecureChat/src/client/clientTruststore.jks";
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
        SocketFactory factory = sslContext.getSocketFactory();
        // Server KeyStore file
        System.setProperty("javax.net.ssl.keyStore", "/home/luca/IdeaProjects/JavaSecureChat/src/client/clientKeystore.jks");
        // Server KeyStore password
        System.setProperty("javax.net.ssl.keyStorePassword", "client");
        // Client TrustStore file
        System.setProperty("javax.net.ssl.trustStore", "/home/luca/IdeaProjects/JavaSecureChat/src/client/clientTruststore.jks");
        // Client TrustStore password
        System.setProperty("javax.net.ssl.trustStorePassword", "client");
        //SocketFactory factory = SSLSocketFactory.getDefault();
        socket = (SSLSocket) factory.createSocket(serverAddress, SERVER_PORT);
        System.out.println("Connection successful after verifying the server's certificate in this truststore");
        // Make connection and initialize streams
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        // Set as a client
        socket.setUseClientMode(true);
        /**
         * Calling startHandshake which explicitly begins handshakes, eventually any attempt to read or write application
         * data on this socket causes an implicit handshake
         */
        socket.startHandshake();
        System.out.println("Cipher suite: " + socket.getSession().getCipherSuite());
        System.out.println("Ciphers enabled:");
        for(String s : socket.getSupportedCipherSuites()) {
            System.out.println(s);
        }
        // Set the list of supported ciphers
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
        // Process all messages from server, according to the protocol
        while (true) {
            String line = in.readLine();
            if (line.startsWith("SUBMIT_NAME")) {
                out.println(getName());
                out.flush();
            } else if (line.startsWith("NAME_ACCEPTED"))
                textField.setEditable(true);
            else if (line.startsWith("NEW_USER"))
                messageArea.append("*** A new user " + line.substring(8) + " entered the chat room !!! ***\n");
            else if (line.startsWith("USERLIST_BEGIN")) {
                usersArea.setText("Online users:\n");
                line = in.readLine();
                while (!line.startsWith("USERLIST_END")) {
                    usersArea.append(line + "\n");
                    line = in.readLine();
                }
            }
            else if (line.startsWith("REMOVE_USER"))
                messageArea.append("*** A user " + line.substring(11) + " exited the chat room !!! ***\n");
            else if (line.startsWith("MESSAGE"))
                messageArea.append(line.substring(8) + "\n");
            else if (line.equals("EXIT")) {
                in.close();
                out.close();
                socket.close();
                System.out.println("Exited");
                System.exit(0);
            }
        }
    }

    // Runs the client as an application with a closeable frame.
    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.run();
    }
}