package client;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;

/**
 * A simple Swing-based client for the chat server.  Graphically
 * it is a frame with a text field for entering messages and a
 * textarea to see the whole dialog.
 *
 * The client follows the Chat Protocol which is as follows.
 * When the server sends "SUBMITNAME" the client replies with the
 * desired screen name.  The server will keep sending "SUBMITNAME"
 * requests as long as the client submits screen names that are
 * already in use.  When the server sends a line beginning
 * with "NAMEACCEPTED" the client is now allowed to start
 * sending the server arbitrary strings to be broadcast to all
 * chatters connected to the server.  When the server sends a
 * line beginning with "MESSAGE " then all characters following
 * this string should be displayed in its message area.
 */
public class ChatClient {

    SSLSocket socket;
    BufferedReader in;
    PrintWriter out;
    String serverAddress;
    JFrame frame = new JFrame("Client chat");
    JTextField textField = new JTextField("Type here...", 40);
    JTextArea messageArea = new JTextArea(10, 40);
    JTextArea usersArea = new JTextArea(10, 10);
    JButton exitButton = new JButton("Exit");

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
        exitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //Execute when button is pressed
                System.out.println("Exiting...");
                out.println("EXIT");
                out.flush();
            }
        });
    }

    /**
     * Prompt for and return the address of the server.
     */
    private String getServerAddress() {
        return JOptionPane.showInputDialog(
                frame,
                "Enter IP Address of the Server:",
                "Welcome to the Chatter",
                JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * Prompt for and return the desired screen name.
     */
    private String getName() {
        return JOptionPane.showInputDialog(
                frame,
                "Choose a screen name:",
                "Screen name selection",
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Connects to the server then enters the processing loop.
     */
    private void run() throws IOException {

        serverAddress = getServerAddress();

        System.setProperty("javax.net.ssl.trustStore","/home/luca/IdeaProjects/JavaSecureChat/src/client/clientTruststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword","client");

        SocketFactory factory = SSLSocketFactory.getDefault();
        socket = (SSLSocket) factory.createSocket(serverAddress,9001);

        System.out.println("Connessione avvenuta correttamente dopo aver verificato se il certificato del server � presente nel truststore\n");

        // Make connection and initialize streams
        //socket = new Socket(serverAddress, 9001);
        in = new BufferedReader(new InputStreamReader(
                socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        //mi "setto" come client
        socket.setUseClientMode(true);
        //eseguo subito l'handshake per evitare che lo faccia solamente alla prima lettura o scrittura
        socket.startHandshake();

        System.out.println("Meccanismi di cifratura abilitati:");
        for(String s : socket.getSupportedCipherSuites()) {
            System.out.println(s);
        }
        System.out.println("");
        //setto la lista dei cifrari supportati come cifrari utilizzabili
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());

        // Process all messages from server, according to the protocol.
        while (true) {
            String line = in.readLine();
            if (line.startsWith("SUBMITNAME")) {
                out.println(getName());
                out.flush();
            } else if (line.startsWith("NAMEACCEPTED")) {
                textField.setEditable(true);
            }
            else if (line.startsWith("NEWUSER")) {
                messageArea.append("*** A new user " + line.substring(8) + " entered the chat room !!! ***\n");
            }
            else if (line.startsWith("USERLIST_START")) {
                usersArea.setText("Online users:\n");
                line = in.readLine();
                while (!line.startsWith("USERLIST_STOP")) {
                    usersArea.append(line + "\n");
                    line = in.readLine();
                }
            }
            else if (line.startsWith("REMOVEUSER")) {
                messageArea.append("*** A user " + line.substring(11) + " exited the chat room !!! ***\n");
            }
            else if (line.startsWith("MESSAGE")) {
                messageArea.append(line.substring(8) + "\n");
            }
            else if (line.equals("EXIT")) {
                in.close();
                out.close();
                socket.close();
                System.out.println("Exited");
                System.exit(0);
            }
        }
    }

    /**
     * Runs the client as an application with a closeable frame.
     */
    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.run();
    }
}
