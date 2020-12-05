package whiteboard;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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
    ServerSocket serverSocket;
    String address;
    int port = 3300;
    boolean isHost = false;
    int hostId;
    int id;
    int nextId = 1;
    HashMap<Integer, Pair<String, Integer>> members;
    Canvas board;
    ArrayList<Pair<Pair<Double,Double>,Pair<Double,Double>>> lineHistory;
    
    
    @Override
    public void start(Stage primaryStage) {
        Parameters params = getParameters();
        List<String> list = params.getRaw();
        if (list.size() > 0) {
            port = Integer.parseInt(list.get(0));
        }
        
        try {
            address = InetAddress.getLocalHost().getHostAddress();
        } catch (IOException error) {
            System.out.println("Failed to get ip address");
            System.out.println(error);
            System.exit(1);
        };
        
        System.out.println(address);
        
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException error) {
            System.out.println("Failed to create a server socket");
            System.out.println(error);
            System.exit(1);
        };
        
        final MutableDouble pX = new MutableDouble(-1);
        final MutableDouble pY = new MutableDouble(-1);
        
        Group root = new Group();
        
        Button host = new Button();
        host.setText("Host");
        Button join = new Button();
        join.setText("Join");
        TextField addressPortField = new TextField ();
        
        VBox box = new VBox();
        box.getChildren().add(host);
        box.getChildren().add(addressPortField);
        box.getChildren().add(join);
        
        board = new Canvas(920, 670);
        GraphicsContext gc = board.getGraphicsContext2D();
        gc.setStroke(Color.BLUE);

        board.setOnMousePressed(e -> {
            pX.setValue(e.getSceneX()); pY.setValue(e.getSceneY());
        });

        board.setOnMouseDragged(e -> {
            double x = e.getSceneX(), y = e.getSceneY();
            String text = "x: " + x + ", y: " + y;
            System.out.println(text);
            Pair<Pair<Double,Double>,Pair<Double,Double>> line = new Pair(
                    new Pair(pX.getValue(),pY.getValue()), new Pair(x,y)
            );
            lineHistory.add(line);
            gc.strokeLine(pX.getValue(),pY.getValue(), x,y);
            pX.setValue(x);
            pY.setValue(y);
            
            new Thread (() -> {
                if (id != hostId) {
                    String hostAddress = members.get(hostId).getKey();
                    int hostPort = members.get(hostId).getValue();
                    try {
                        sendLine(hostAddress, hostPort, line);
                    } catch (IOException error) {
                        System.out.println("Failed to connect to host");
                        System.out.println(error);
                        System.exit(1);
                    }
                }else {
                    members.entrySet().forEach(member -> {
                        int memberId = member.getKey();
                        if (memberId != id) {
                            String memberAddress = member.getValue().getKey();
                            int memberPort = member.getValue().getValue();
                            try {
                                sendLine(memberAddress, memberPort, line);
                            } catch (IOException error) {
                                System.out.println("Failed to send line");
                                System.out.println(error);
                                System.exit(1);
                            }
                        }
                    });
                }
            }).start();
        });

        board.setOnMouseReleased(e -> {
            pX.setValue(-1); pY.setValue(-1);
        });
        
        host.setOnAction(event -> {
            isHost = true;
            id = nextId++;
            hostId = id;
            members = new HashMap<>();
            lineHistory = new ArrayList<>();
            members.put(id, new Pair(address, port));
            createServerThread(gc);
            root.getChildren().add(board);
            root.getChildren().remove(box);
        });
        
        join.setOnAction(event -> {
            String addressPortText = addressPortField.getText();
            String[] parts = addressPortText.split(":");
            if (parts.length == 2) {
                new Thread(() -> {
                    String hostAddress = parts[0];
                    int hostPort = Integer.parseInt(parts[1]);
                    System.out.println(hostAddress + ":" + hostPort);

                    try {
                        Socket socket = new Socket(hostAddress, hostPort);
                        // PrintStream
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        out.writeUTF("join");
                        out.writeUTF(address);
                        out.writeInt(port);
                        out.flush();
                        hostId = in.readInt();
                        id = in.readInt();
                        lineHistory = (ArrayList<Pair<Pair<Double,Double>,Pair<Double,Double>>>) in.readObject();
                        members =  (HashMap<Integer, Pair<String, Integer>>) in.readObject();
                        
                        System.out.println(id);
                        System.out.println(lineHistory);
                        System.out.println(members);
                        
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
                        System.out.println("Failed to listen");
                        System.out.println(error);
                        System.exit(1);
                    } catch (ClassNotFoundException error) {
                        System.out.println("Failed to get line history");
                        System.out.println(error);
                        System.exit(1);
                    }
                }).start();
                
                createServerThread(gc);
                
                root.getChildren().add(board);
                root.getChildren().remove(box);
            }
        });
        
        root.getChildren().add(box);
        
        Scene scene = new Scene(root, 950, 700);
        
        primaryStage.setTitle("Whiteboard!");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
    }
    
    public void shutdown() {
        System.exit(0);
    }
    
    public void joinResponse(ObjectInputStream in, ObjectOutputStream out, Socket clientSocket) throws IOException {
        String memberAdress = in.readUTF();
        int memberPort = in.readInt();
        int memberId = nextId++;
        members.put(memberId, new Pair(memberAdress, memberPort));
        out.writeInt(id);
        out.writeInt(memberId);
        out.writeObject(lineHistory);
        out.writeObject(members);
        out.flush();
    }
    
    public void leaveResponse(ObjectInputStream in, ObjectOutputStream out, Socket clientSocket) {
        
    }
    
    public void updateResponse(ObjectInputStream in, ObjectOutputStream out, Socket clientSocket) {
        
    }
    
    public void pingResponse(ObjectInputStream in, ObjectOutputStream out, Socket clientSocket) {
        
    }
    
    public void sendLine(String targetAddress, int targetPort, Pair<Pair<Double,Double>,Pair<Double,Double>> line) throws IOException {
        Socket socket = new Socket(targetAddress, targetPort);
        ObjectOutputStream out = new ObjectOutputStream( socket.getOutputStream() );
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        out.writeUTF("draw");
        out.writeInt(id);
        out.writeObject(line);
        out.flush();

        in.close();
        out.close();
        socket.close();
    }
    
    public void createServerThread(GraphicsContext gc) {
        new Thread(() -> {
            try {
                while(true) {
                    Socket clientSocket = serverSocket.accept();
                    ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                    String inputline = in.readUTF();
                    // check this
                    if (inputline == null) continue;
                    switch (inputline) {
                        case "join":
                            joinResponse(in, out, clientSocket);
                            break;
                        case "leave":
                            leaveResponse(in, out, clientSocket);
                            break;
                        case "update":
                            updateResponse(in, out, clientSocket);
                            break;
                        case "ping":
                            pingResponse(in, out, clientSocket);
                            break;
                        case "draw":
                            int senderId = in.readInt();
                            Pair<Pair<Double,Double>,Pair<Double,Double>> line = 
                                    (Pair<Pair<Double,Double>,Pair<Double,Double>>) in.readObject();
                            double startX = line.getKey().getKey();
                            double startY = line.getKey().getValue();
                            double endX = line.getValue().getKey();
                            double endY = line.getValue().getValue();
                            gc.strokeLine(startX, startY, endX, endY);
                            lineHistory.add(line);
                            if (id == hostId) {
                                members.entrySet().forEach(member -> {
                                    int memberId = member.getKey();
                                    if (memberId != id && memberId != senderId) {
                                        String memberAddress = member.getValue().getKey();
                                        int memberPort = member.getValue().getValue();
                                        try {
                                            sendLine(memberAddress, memberPort, line);
                                        } catch (IOException error) {
                                            System.out.println("Failed to send line");
                                            System.out.println(error);
                                            System.exit(1);
                                        }
                                    }
                                });
                            }
                            break;
                        default:
                            System.out.println("Invalid request");
                    }

                    in.close();
                    out.close();
                    clientSocket.close();
                }

            } catch (IOException error) {
                System.out.println("Failed to listen");
                System.out.println(error);
                System.exit(1);
            } catch (ClassNotFoundException error) {
                System.out.println("Failed to get line");
                System.out.println(error);
                System.exit(1);
            }
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
    
}
