package whiteboard;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import static java.util.Collections.sort;
import java.util.HashMap;
import java.util.List;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Pair;

public class Whiteboard extends Application {
    int MAX_TRIES = 2;
    ServerSocket serverSocket;
    String address;
    int port = 3300;
    
    int hostId;
    int id;
    int nextId = 1;
    HashMap<Integer, Pair<String, Integer>> members;
    
    Canvas board;
    GraphicsContext gc;
    // the list of the lines drawn so far
    ArrayList<Pair<Pair<Double,Double>,Pair<Double,Double>>> lineHistory;
    
    // values used to try to quickly reduce at least some of the performance problems
    // caused by downed nodes
    int lastHostChanged;
    int lastLostMember;
    
    
    @Override
    public void start(Stage primaryStage) {
        Parameters params = getParameters();
        List<String> list = params.getRaw();
        
        // get port if one is as a argument
        if (list.size() > 0) {
            port = Integer.parseInt(list.get(0));
        }
        
        //get the current address
        try {
            address = InetAddress.getLocalHost().getHostAddress();
        } catch (IOException error) {
            System.out.println("Failed to get ip address");
            System.out.println(error);
            System.exit(1);
        };
        
        System.out.println(address);
        
        // create the socket others can connect to
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException error) {
            System.out.println("Failed to create a server socket");
            System.out.println(error);
            System.exit(1);
        };
        
        // initialize the values telling the previous position of the mouse
        final MutableDouble pX = new MutableDouble(-1);
        final MutableDouble pY = new MutableDouble(-1);
        
        Group root = new Group();
        
        // buttons and text field to choose hosting or joinin a whiteboard
        Button host = new Button();
        host.setText("Host");
        Button join = new Button();
        join.setText("Join");
        TextField addressPortField = new TextField ();
        
        VBox box = new VBox();
        box.getChildren().add(host);
        box.getChildren().add(addressPortField);
        box.getChildren().add(join);
        
        // just creating the canvas for the whiteboard
        board = new Canvas(920, 670);
        gc = board.getGraphicsContext2D();
        gc.setStroke(Color.BLUE);

        board.setOnMousePressed(e -> {
            // set the initial position when starting drawing
            pX.setValue(e.getSceneX()); pY.setValue(e.getSceneY());
            // away to see some of the values inside the nodes by clicking
            System.out.println(hostId);
            System.out.println(id);
            System.out.println(members);
            System.out.println(nextId);
        });

        board.setOnMouseDragged(e -> {
            // create the line based on the previous position and current position
            double x = e.getSceneX(), y = e.getSceneY();
            Pair<Pair<Double,Double>,Pair<Double,Double>> line = new Pair(
                    new Pair(pX.getValue(),pY.getValue()), new Pair(x,y)
            );
            lineHistory.add(line);
            gc.strokeLine(pX.getValue(),pY.getValue(), x,y);
            pX.setValue(x);
            pY.setValue(y);
            
            // make a new thread that handles sending the line to host
            // or all the other members if the node is the host
            new Thread (() -> {
                sendDrawing(line);
            }).start();
        });

        board.setOnMouseReleased(e -> {
            pX.setValue(-1); pY.setValue(-1);
        });
        
        // become the host
        host.setOnAction(event -> {
            // give yourself an id
            id = nextId++;
            // mark yourself as the host
            hostId = id;
            // initialize members and lineHistory
            members = new HashMap<>();
            lineHistory = new ArrayList<>();
            // makeyourself a member
            members.put(id, new Pair(address, port));
            createServerThread();
            
            // remove the buttons and add the whiteboard canvas
            root.getChildren().add(board);
            root.getChildren().remove(box);
        });
        
        // join a host
        join.setOnAction(event -> {
            // the text is supposed to be <ip>:<port>
            String addressPortText = addressPortField.getText();
            String[] parts = addressPortText.split(":");
            if (parts.length == 2) {
                // create thread to communicate with host (uneccessary)
                new Thread(() -> {
                    // get the address and port
                    String hostAddress = parts[0];
                    int hostPort = Integer.parseInt(parts[1]);
                    System.out.println(hostAddress + ":" + hostPort);

                    try {
                        Socket socket = new Socket(hostAddress, hostPort);
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        
                        // write the reason of contact, own address and own port
                        out.writeUTF("join");
                        out.writeUTF(address);
                        out.writeInt(port);
                        out.flush();
                        
                        // read the host's id and your own new id
                        hostId = in.readInt();
                        id = in.readInt();
                        // the nextt id is the one just given +1
                        nextId = id+1;
                        
                        // read the line hostory and members given by host
                        lineHistory = (ArrayList<Pair<Pair<Double,Double>,Pair<Double,Double>>>) in.readObject();
                        members =  (HashMap<Integer, Pair<String, Integer>>) in.readObject();
                        
                        // draw the line history
                        lineHistory.forEach((line) -> {
                            double startX = line.getKey().getKey();
                            double startY = line.getKey().getValue();
                            double endX = line.getValue().getKey();
                            double endY = line.getValue().getValue();
                            gc.strokeLine(startX, startY, endX, endY);
                        });
                        in.close();
                        out.close();
                        socket.close();
                        
                    } catch (IOException error) {
                        System.out.println("Failed to join");
                        System.out.println(error);
                        System.exit(1);
                    } catch (ClassNotFoundException error) {
                        System.out.println("Failed to get line history");
                        System.out.println(error);
                        System.exit(1);
                    }
                }).start();
                
                createServerThread();
                
                // remove the buttons and add the whiteboard canvas
                root.getChildren().add(board);
                root.getChildren().remove(box);
            }
        });
        
        root.getChildren().add(box);
        
        Scene scene = new Scene(root, 950, 700);
        
        primaryStage.setTitle("Whiteboard!");
        primaryStage.setScene(scene);
        // custom function for shutting down the programm
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
    }
    
    public void shutdown() {
        // just exit when no host or join button is pressed
        if (id == 0) System.exit(0);
        
        if (id == hostId) {
            int newHostId = findNewHost(false);
            // the value is -1 if no viable host was found
            if (newHostId >= 0) {
                members.remove(id);
                sendChangeHost(newHostId);
            }
        } else {
            String hostAddress = members.get(hostId).getKey();
            int hostPort = members.get(hostId).getValue();
            try {
                sendRemoveMemberNotif(hostAddress, hostPort, id);
            } catch (IOException error) {
                System.exit(0);
            }
        }
        
        System.exit(0);
    }
    
    public int findNewHost(boolean includeSelf) {
        // sort the keys to get the smallest key
        ArrayList<Integer> keys = new ArrayList(members.keySet());
        Collections.sort(keys);
        boolean isAlive = false;
        // will retrun -1 if no viable host is found
        int targetId = -1;
        while (keys.size() > 0 && !isAlive) {
            targetId = keys.get(0);
            // special interaction with selecting the node itself
            if (targetId == id) {
                if (includeSelf) break;
                keys.remove(0);
                continue;
            } 
            // ping to make sure the selected node is alive
            try {
                isAlive = ping(targetId);
            } catch (IOException error) {
                System.out.println("failed to ping");
                System.out.println(error);
                isAlive = false;
                keys.remove(0);
            }
        }
        return targetId;
    }
    
    public void sendChangeHost(int newHostId) {
        members.entrySet().forEach(member -> {
            
            // get the member's info
            int memberId = member.getKey();
            String memberAddress = member.getValue().getKey();
            int memberPort = member.getValue().getValue();
            
            if (memberId == id) return;
            
            try {
                Socket socket = new Socket(memberAddress, memberPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                
                // send the reason and new host id
                out.writeUTF("new_host");
                out.writeInt(newHostId);
                out.flush();
                
                in.close();
                out.close();
                socket.close();
            } catch (IOException error) {
                System.out.println("failed to send change host message");
                System.out.println(error);
                // this is missing something
            }
        });
    }
    
    public void joinResponse(ObjectOutputStream out, int memberId) throws IOException {
        
        // send id of host, the id of the new member, line history and members
        // for the new member
        out.writeInt(hostId);
        out.writeInt(memberId);
        out.writeObject(lineHistory);
        out.writeObject(members);
        out.flush();
    }
    
    // sends notification to everyone that a new member has joined
    public void sendAddMember(ObjectInputStream in, int newId) throws IOException {
        String newAddress = in.readUTF();
        int newPort = in.readInt();
        
        members.entrySet().forEach(member -> {
            
            // get member info
            int memberId = member.getKey();
            String memberAddress = member.getValue().getKey();
            int memberPort = member.getValue().getValue();
            
            if (memberId == id) return;
            
            // can try to contact multiple times
            int retries = 0;
            while (retries < MAX_TRIES) {
                try {
                    Socket socket = new Socket(memberAddress, memberPort);
                    ObjectOutputStream client_out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream client_in = new ObjectInputStream(socket.getInputStream());
                    
                    //send reason and the new member's id, ip and port
                    client_out.writeUTF("add_member");
                    client_out.writeInt(newId);
                    client_out.writeUTF(newAddress);
                    client_out.writeInt(newPort);
                    client_out.flush();

                    client_out.close();
                    client_in.close();
                    socket.close();
                    break;
                } catch (IOException error) {
                    System.out.println("failed sending add member");
                    System.out.println(error);
                    retries++;
                }
            }
            
            // remove the member if it can't be contacted
            if (retries == MAX_TRIES) removeMember(memberId);
            
        });
        
        // add the new member to members
        members.put(newId, new Pair(newAddress, newPort));
    }
    
    public void pingResponse(ObjectInputStream in, ObjectOutputStream out, Socket clientSocket) throws IOException {
        // asnwer pong to the ping
        out.writeUTF("pong");
        out.flush();
    }
    
    public boolean ping(int targetId) throws IOException {
        // get target info
        String targetAddress = members.get(targetId).getKey();
        int targetPort = members.get(targetId).getValue();
        
        Socket socket = new Socket(targetAddress, targetPort);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        
        // send reason
        out.writeUTF("ping");
        out.flush();
        
        // check if we get pong back
        boolean success = in.readUTF().equals("pong");
        
        in.close();
        out.close();
        socket.close();
        
        return success;
    }
    
    public void removeMember(int removeId) {
        members.remove(removeId);
        
        members.entrySet().forEach(member -> {
            int memberId = member.getKey();
            
            if (memberId == id) return;
            
            String memberAddress = member.getValue().getKey();
            int memberPort = member.getValue().getValue();
            
            // can try multiple times
            int retries = 0;
            while (retries < MAX_TRIES) {
                try {
                    sendRemoveMemberNotif(memberAddress, memberPort, removeId);
                    break;
                } catch (IOException error) {
                    System.out.println("Failed to send remove member");
                    retries++;
                    System.out.println(error);
                }
            }
            
            // remove member if they can't be reached
            if (retries == MAX_TRIES) {
                removeMember(memberId);
            }
        });
    }
    
    public void sendRemoveMemberNotif(String targetAddress, int targetPort, int removeId) throws IOException {
        Socket socket = new Socket(targetAddress, targetPort);
        ObjectOutputStream out = new ObjectOutputStream( socket.getOutputStream() );
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        
        // send reason and the id of the member to be removed
        out.writeUTF("remove_member");
        out.writeInt(removeId);
        out.flush();

        in.close();
        out.close();
        socket.close();
    }
    
    public void sendLine(String targetAddress, int targetPort, Pair<Pair<Double,Double>,Pair<Double,Double>> line) throws IOException {
        Socket socket = new Socket(targetAddress, targetPort);
        ObjectOutputStream out = new ObjectOutputStream( socket.getOutputStream() );
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        
        // send reason, the id of the drawer (itself) and the drawn line
        out.writeUTF("draw");
        out.writeInt(id);
        out.writeObject(line);
        out.flush();

        in.close();
        out.close();
        socket.close();
    }
    
    public void drawRecievedLine(int senderId, Pair<Pair<Double,Double>,Pair<Double,Double>> line) {
        double startX = line.getKey().getKey();
        double startY = line.getKey().getValue();
        double endX = line.getValue().getKey();
        double endY = line.getValue().getValue();
        
        // draw the line and add it to history
        gc.strokeLine(startX, startY, endX, endY);
        lineHistory.add(line);
        
        if (id == hostId) {
            members.entrySet().forEach(member -> {
                int memberId = member.getKey();
                
                if (memberId != id && memberId != senderId) {
                    // get member info
                    String memberAddress = member.getValue().getKey();
                    int memberPort = member.getValue().getValue();
                    
                    // can try to contact multiple times
                    int retries = 0;
                    while (retries < MAX_TRIES) {
                        try {
                            sendLine(memberAddress, memberPort, line);
                            break;
                        } catch (IOException error) {
                            System.out.println("Failed to send line");
                            System.out.println(error);
                            retries++;
                        }
                    }
                    
                    // remove the member if it can't be reached
                    if (retries == MAX_TRIES) removeMember(memberId);
                }
            });
        }
    }
    
    public void sendDrawing(Pair<Pair<Double,Double>,Pair<Double,Double>> line) {
        int currentHostId = hostId;
        
        if (id != currentHostId) {
            // get host info
            String hostAddress = members.get(currentHostId).getKey();
            int hostPort = members.get(currentHostId).getValue();
            
            // can try to contact multiple times
            int retries = 0;
            while (retries < MAX_TRIES) {
                try {
                    sendLine(hostAddress, hostPort, line);
                    break;
                } catch (IOException error) {
                    System.out.println("Failed to connect to host");
                    System.out.println(error);
                    retries++;
                }
            }
            
            // if it can't be contacted remove it and select new host
            if (retries == MAX_TRIES) {
                // trying to (slightly) migitate multiple threads from doing the same thing
                if (lastHostChanged != currentHostId) {
                    lastHostChanged = currentHostId;
                    members.remove(currentHostId);
                    
                    // find new host and select it
                    int newHostId = findNewHost(true);
                    hostId = newHostId;
                    sendChangeHost(newHostId);
                }
                // try to send the line again
                sendDrawing(line);
            }
        }else {
            members.entrySet().forEach(member -> {
                int memberId = member.getKey();
                
                if (memberId != id) {
                    String memberAddress = member.getValue().getKey();
                    int memberPort = member.getValue().getValue();
                    
                    // can try to contact multiple times
                    int retries = 0;
                    while (retries < MAX_TRIES) {
                        try {
                            sendLine(memberAddress, memberPort, line);
                            break;
                        } catch (IOException error) {
                            System.out.println("Failed to send line");
                            retries++;
                            System.out.println(error);
                        }
                    }
                    
                    // if the member can't be contacted remove it
                    if (retries == MAX_TRIES) {
                        // trying to (slightly) migitate multiple threads from doing the same thing
                        if (memberId != lastLostMember) {
                            lastLostMember = memberId;
                            removeMember(memberId);
                        }
                    }
                }
            });
        }
    }
    
    public void createServerThread() {
        new Thread(() -> {
            // constant server thread
            while (true) {
                try {
                        // accept a connection
                        Socket clientSocket = serverSocket.accept();
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                        
                        // get the reason for the contact
                        String inputline = in.readUTF();
                        System.out.println(clientSocket.getLocalAddress());
                        
                        if (inputline == null) continue;
                        
                        switch (inputline) {
                            case "join":
                                int memberId = nextId++;
                                // send everyone info about the new member
                                sendAddMember(in, memberId);
                                // respond to the new member
                                joinResponse(out, memberId);
                                break;
                            case "ping":
                                pingResponse(in, out, clientSocket);
                                break;
                            case "draw":
                                int senderId = in.readInt();
                                Pair<Pair<Double,Double>,Pair<Double,Double>> line = 
                                        (Pair<Pair<Double,Double>,Pair<Double,Double>>) in.readObject();
                                
                                drawRecievedLine(senderId, line);
                                break;
                            case "remove_member":
                                int removedId = in.readInt();
                                if (id == hostId) {
                                    removeMember(removedId);
                                } else {
                                    members.remove(removedId);
                                }
                                break;
                            case "add_member":
                                int newId = in.readInt();
                                String newAddress = in.readUTF();
                                int newPort = in.readInt();
                                members.put(newId, new Pair(newAddress, newPort));
                                nextId++;
                                break;
                            case "new_host":
                                int newHostId = in.readInt();
                                if (newHostId != hostId) {
                                    members.remove(hostId);
                                    hostId = newHostId;
                                }
                                break;
                            default:
                                System.out.println("Invalid request");
                        }

                        in.close();
                        out.close();
                        clientSocket.close();
                        
                } catch (IOException error) {
                    System.out.println("Failed to listen");
                    System.out.println(error);
                } catch (ClassNotFoundException error) {
                    System.out.println("Failed to get line");
                    System.out.println(error);
                    System.exit(1);
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
    
}
