package edu.yu.cs.com3800.stage4;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayServer implements LoggingServer {
    class PostHandler implements HttpHandler {
        private volatile GatewayPeerServerImpl gpsi;
        private Logger l;
        private final InetSocketAddress myTCPAddress;
        public PostHandler(GatewayPeerServerImpl gpsi, Logger l, InetSocketAddress myTCPAddress){
            this.gpsi=gpsi;
            this.l=l;
            this.myTCPAddress=myTCPAddress;
        }
        public void handle(HttpExchange t) throws IOException {
            try {
                while(this.gpsi.getCurrentLeader()==null){
                    Thread.sleep(100);
                }
                InetSocketAddress UDPAddressOfLeader=this.gpsi.getPeerByID(this.gpsi.getCurrentLeader().getProposedLeaderID());
                if(!t.getRequestHeaders().get("Content-Type").get(0).equals("text/x-java-source")){
                    this.l.fine("received a request with the wrong content type, response will include an error");
                    String response="content was not of correct type";
                    File f=new File("message.txt");
                    try {
                        Files.createFile(Path.of("message.txt"));
                        Files.writeString(Path.of("message.txt"),"type was "+t.getRequestHeaders().get("Content-Type").get(0));
                        this.l.fine("type was"+t.getRequestHeaders().get("Content-Type").get(0));
                        t.sendResponseHeaders(400,response.length());
                        OutputStream os = t.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    } catch (IOException e) {
                        this.l.warning("IOException while sending back response that request had some errors in it");
                    }
                }
                else {
                    try(Socket socketToLeader=new Socket(UDPAddressOfLeader.getHostName(),UDPAddressOfLeader.getPort()+2);
                        InputStream inputFromLeader = socketToLeader.getInputStream();
                        OutputStream outputToLeader = socketToLeader.getOutputStream()){
                        byte[] b=t.getRequestBody().readAllBytes();
                        outputToLeader.write(new Message(Message.MessageType.WORK, b, this.myTCPAddress.getHostString(), this.myTCPAddress.getPort(), UDPAddressOfLeader.getHostName(), UDPAddressOfLeader.getPort()+2).getNetworkPayload());
                        this.l.log(Level.FINEST,"sent output to the leader");
                        Message completedMessage= new Message(Util.readAllBytesFromNetwork(inputFromLeader));
                        this.l.log(Level.FINEST,"received the response from the worker: \n"+completedMessage.toString());
                        t.sendResponseHeaders(200, completedMessage.getMessageContents().length);
                        OutputStream os = t.getResponseBody();
                        os.write(completedMessage.getMessageContents());
                        os.close();
                    } catch (IOException e) {
                        this.l.warning("failed to compile and run the code contained in the request body, responding with error");
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        PrintStream printStream = new PrintStream(outputStream);
                        e.printStackTrace(printStream);
                        String response = e.getMessage() + "\n" + outputStream.toString();
                        try {
                            t.sendResponseHeaders(400, response.length());
                            OutputStream os = t.getResponseBody();
                            os.write(response.getBytes());
                            os.close();
                        } catch (IOException ex){
                            this.l.log(Level.WARNING, "failed to send back response");
                        }
                    }
                    finally {
                        t.close();
                    }
                }
            } catch (InterruptedException e) {
                this.l.log(Level.WARNING,"thread was interrupted before leader was set");
            }
        }
    }
    private int httpPort;
    private HttpServer server;
    private GatewayPeerServerImpl gpsi;
    private Logger logger;
    private InetSocketAddress myTCPAddress;
    private ThreadPoolExecutor tpe;
    public GatewayServer(int httpPort, GatewayPeerServerImpl gatewayPeerServerImpl){
        this.httpPort=httpPort;
        this.gpsi= gatewayPeerServerImpl;
        this.myTCPAddress=new InetSocketAddress(this.gpsi.getAddress().getHostName(),this.gpsi.getAddress().getPort()+2);
        try{
            logger=initializeLogging("logs/Gateway-Log", GatewayServer.class.getCanonicalName() + "-using-worker-on-Port-" + this.httpPort);
        }catch (IOException e) {
            e.printStackTrace();
        }
        while(true){
            try {
                this.server = HttpServer.create(new InetSocketAddress(httpPort), 0);
                break;
            } catch (IOException e) {
                this.logger.log(Level.FINE,"couldn't create HTTPServer, retrying");
            }
        }
        this.tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(12);
        server.createContext("/compileandrun", new PostHandler(this.gpsi,this.logger,new InetSocketAddress(this.myTCPAddress.getHostName(),this.myTCPAddress.getPort())));
        server.setExecutor(tpe);
    }
    public void start() {
        this.server.start();
    }
    public void stop() {
        tpe.shutdown();
        this.server.stop(3);
    }
}
