package edu.yu.cs.com3800.stage1;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.SimpleServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SimpleServerImpl implements SimpleServer {
    class PostHandler implements HttpHandler {
        private Logger l;
        public PostHandler(Logger l){
            this.l=l;
        }
        public void handle(HttpExchange t) throws IOException {
            JavaRunner jr = new JavaRunner();
            if(!t.getRequestHeaders().get("Content-Type").get(0).equals("text/x-java-source")){
                l.info("received a request with the wrong content type, response will include an error");
                String response="content was not of correct type";
                File f=new File("message.txt");
                Files.createFile(Path.of("message.txt"));
                Files.writeString(Path.of("message.txt"),"type was "+t.getRequestHeaders().get("Content-Type").get(0));
                l.info("type was"+t.getRequestHeaders().get("Content-Type").get(0));
                t.sendResponseHeaders(400,response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
            else {
                try {
                    byte[] b=t.getRequestBody().readAllBytes();
                    String response = jr.compileAndRun(new ByteArrayInputStream(b));
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    l.info("failed to compile and run the code contained in the request body, responding with error");
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PrintStream printStream = new PrintStream(outputStream);
                    e.printStackTrace(printStream);
                    String response = e.getMessage() + "\n" + outputStream.toString();
                    t.sendResponseHeaders(400, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
                finally {
                    t.close();
                }
            }
        }
    }
    private int port;
    private HttpServer server;
    private Logger serverLog;
    public SimpleServerImpl(int port) throws IOException {
        this.serverLog=Logger.getLogger("serverLog");
        FileHandler fh = null;
        try {
            fh = new FileHandler("serverLog.log");
        } catch (IOException e) {
            System.err.println("failed to add File Handler to the logger");
        }
        serverLog.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);
        this.port=port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/compileandrun", new PostHandler(this.serverLog));
        this.server.setExecutor(null); // creates a default executor
    }

    @Override
    public void start() {
        this.server.start();
    }

    @Override
    public void stop() {
        this.server.stop(0);
    }
    public static void main(String[] args) {
        int port = 9000;
        if (args.length > 0){
            port = Integer.parseInt(args[0]);
        }
        SimpleServer myserver = null;
        try {
            myserver = new SimpleServerImpl(port);
            myserver.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            myserver.stop();
        }
    }
}
