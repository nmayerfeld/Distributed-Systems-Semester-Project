package edu.yu.cs.com3800.stage1;

import edu.yu.cs.com3800.stage1.Client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;

public class ClientImpl implements Client {
    private URI uri;
    private URL url;
    private HttpClient client;
    private int hostPort;
    private Response response;
    private Logger clientLog;
    public ClientImpl(String hostName, int hostPort) throws MalformedURLException{
        this.clientLog=Logger.getLogger("clientLog");
        FileHandler fh = null;
        try {
            fh = new FileHandler("clientLog.log");
        } catch (IOException e) {
            System.err.println("failed to add File Handler to the logger");
        }
        clientLog.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);
        this.client=HttpClient.newBuilder().build();
        this.url=new URL("http://"+hostName+":"+hostPort+"/compileandrun");
        this.hostPort=hostPort;
    }
    @Override
    public void sendCompileAndRunRequest(String src) throws IOException {
        HttpRequest request= null;
        try {
            request = HttpRequest
                    .newBuilder(this.url.toURI())
                    .headers("Content-Type","text/x-java-source")
                    .POST(HttpRequest.BodyPublishers.ofString(src))
                    .build();
        } catch (URISyntaxException e) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream);
            e.printStackTrace(printStream);
            clientLog.info("URISyntax Exception occurred when converting url to uri\n"+outputStream.toString());
        }
        CompletableFuture<HttpResponse<String>> requestResponse = this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> hr=requestResponse.get();
            this.response=new Response(hr.statusCode(),hr.body());
        } catch (InterruptedException e) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream);
            e.printStackTrace(printStream);
            clientLog.info("Interrupted Exception occurred when getting response from request\n"+outputStream.toString());
        } catch (ExecutionException e) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream);
            e.printStackTrace(printStream);
            clientLog.info("Execution Exception occurred when getting response from request\n"+outputStream.toString());
        }
    }

    @Override
    public Response getResponse() throws IOException {
        return this.response;
    }
}
