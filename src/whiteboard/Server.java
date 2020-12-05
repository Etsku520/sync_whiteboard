package whiteboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;


public class Server {
    private ServerSocket serverSocket;
    private boolean host;
    
    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException error) {
            System.out.println("Failed to create a server socket");
            System.out.println(error);
            System.exit(1);
        };
        host = false;
    }
    
    public void listen() {
        if (host) {
            new Thread(() -> {
                try {
                    while(true) {
                        System.out.println("start");
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("accepted");
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String inputline = in.readLine();
                        if (inputline.equals("join")) {
                            
                        }
                        if (inputline.equals("leave")) {
                            
                        }
                        if (inputline.equals("leave")) {
                            
                        }
                        out.println("hello");
                        clientSocket.shutdownOutput();
                        clientSocket.shutdownInput();
                        System.out.println("close");
                    }

                } catch (IOException error) {
                    System.out.println("Failed to listen");
                    System.out.println(error);
                    System.exit(1);
                };
            }).start();
        }
    }
    
    public void setHost(boolean host) {
        this.host = host;
    }
    

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
}
