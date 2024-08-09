package edu.yu.cs.com3800.stage1;

import edu.yu.cs.com3800.SimpleServer;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Stage1Test {
    private static ClientImpl client;
    private static SimpleServerImpl s;
    @BeforeAll
    public static void setUp() throws IOException {
        s = new SimpleServerImpl(8080);
        s.start();
    }
    @AfterAll
    public static void tearDown() {
        s.stop();
    }
    @Test
    public void simpleTest(){
        try {
            client = new ClientImpl("localhost", 8080);
            String src = """

                    public class HelloWorld {

                        public String run() {

                            return "Hello World";

                        }

                    }

                """;


            client.sendCompileAndRunRequest(src);

            var response = client.getResponse();
            System.out.println("Expected response:\n"+"Hello World"+"\nActual Response:\n"+response.getBody());
            assertEquals(200,response.getCode());
            assertEquals("Hello World",response.getBody());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Test
    public void simpleTest2(){
        try {
            client = new ClientImpl("localhost", 8080);
            String src = """

                    public class TestingTesting {

                        public String run() {

                            return "testing testing 1 2 3 " + (2 + 2);

                        }

                    }

                """;

            client.sendCompileAndRunRequest(src);

            var response = client.getResponse();
            System.out.println("Expected response:\n"+"testing testing 1 2 3 4"+"\nActual Response:\n"+response.getBody());
            assertEquals(200,response.getCode());
            assertEquals("testing testing 1 2 3 4",response.getBody());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Test

    public void testStage1() throws IOException {
        var server = new SimpleServerImpl(1111);
        var client = new ClientImpl("localhost", 1111);
        String src = """

                    public class HelloWorld {

                        public String run() {

                            return "Hello " + "World " + (40 + 14 / 7);

                        }

                    }

                """;

        String expected = "Hello World 42";
        server.start();
        client.sendCompileAndRunRequest(src);
        var response = client.getResponse();
        System.out.println("Expected response:");
        System.out.println(expected);
        System.out.println("Actual response:");
        System.out.println(response.getBody());
        assertEquals(expected, response.getBody());
        assertEquals(200, response.getCode());

        client = new ClientImpl("localhost", 1111);
        client.sendCompileAndRunRequest(src);
        response = client.getResponse();
        System.out.println("Expected response:");
        System.out.println(expected);
        System.out.println("Actual response:");
        System.out.println(response.getBody());
        assertEquals(expected, response.getBody());
        assertEquals(200, response.getCode());
        server.stop();

    }
    @Test
    public void badURLTest(){
        try{
            ClientImpl c = new ClientImpl("sofmkveas.dkfif.eoe3044d",-3);
            assert false;
        } catch (MalformedURLException e) {

        }
    }
}
