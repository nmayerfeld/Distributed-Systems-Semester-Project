package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread implements LoggingServer {
    private JavaRunner jr;
    private InetSocketAddress myTCPAddress;
    private ServerSocket s;
    private Logger logger;
    private boolean shutdown=false;
    public JavaRunnerFollower(InetSocketAddress myTCPAddress){
        this.myTCPAddress=myTCPAddress;
        try {
            this.logger = initializeLogging("logs/JRF-Logs",JavaRunnerFollower.class.getCanonicalName() + "-on-follower-server-on-TCP-Port-" + this.myTCPAddress.getPort());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            this.jr=new JavaRunner();
        } catch (IOException e) {
            this.logger.log(Level.SEVERE,"Failed to create JavaRunner object");
            this.shutdown();
        }
        try {
            this.s=new ServerSocket(myTCPAddress.getPort());
        } catch (IOException e) {
            this.logger.log(Level.SEVERE,"failed to create Server Socket, see: "+e.toString());
            this.shutdown();
        }
    }


    public void shutdown() {
        this.logger.log(Level.SEVERE, "Shutting down JRF");
        Thread.currentThread().interrupt();
        this.shutdown=true;
        try {
            this.s.close();
        } catch (IOException e) {
            this.logger.log(Level.SEVERE,"can't close ServerSocket");
        }
    }

    @Override
    public void run(){
        while(!Thread.currentThread().isInterrupted()){
            /*
             * Three kinds of exceptions are being thrown here
                * an IOException
                    * by Socket.accept()
                    * by Socket.getInputStream()
                    * by Socket.getOutputStream()
                    * by OutputStream.write()
                * a ReflectiveOperationException
                    * by JavaRunner.compileAndRun()
                * an IllegalArgumentException
                    * by JavaRunner.compileAndRun()
             * On all IO exceptions, code can log, shutdown, and continue to next iteration of loop
             * However, on ReflectiveOperationException or IllegalArgumentException, code must send a message to InputStream with the error occured flag set
             * The code can't all go in the same try block because then compiler won't allow OutputStream.write() in the catch block
             * Thus, code uses a try within a try
             */
            try (Socket currentSocket= this.s.accept();
                 InputStream in=currentSocket.getInputStream();
                 OutputStream out=currentSocket.getOutputStream()){
                Message m = new Message(Util.readAllBytesFromNetwork(in));
                try {
                    String result = jr.compileAndRun(new ByteArrayInputStream(m.getMessageContents()));
                    logger.log(Level.FINEST,"successfully compiled requestID: " + m.getRequestID() + " result was: " + result);
                    out.write(new Message(Message.MessageType.COMPLETED_WORK, result.getBytes(StandardCharsets.UTF_8), this.myTCPAddress.getHostString(), this.myTCPAddress.getPort(), m.getSenderHost(), m.getSenderPort(), m.getRequestID()).getNetworkPayload());
                } catch (ReflectiveOperationException | IllegalArgumentException e) {
                    logger.log(Level.WARNING,"failed to compile and run request.  Sending response with error occurred flag set");
                    byte[] newMessageContents = "Compilation failed".getBytes(StandardCharsets.UTF_8);
                    out.write(new Message(Message.MessageType.COMPLETED_WORK, newMessageContents, this.myTCPAddress.getHostString(),this.myTCPAddress.getPort(), m.getSenderHost(), m.getSenderPort(),m.getRequestID(),true).getNetworkPayload());
                }
            } catch (IOException e) {
                if(this.shutdown){
                    logger.log(Level.SEVERE,"IOException due to shutdown closing the ServerSocket.  Shutting down");
                    Thread.currentThread().interrupt();
                }
                else{
                    logger.log(Level.SEVERE,"Failed to get the next socket, or write to/from it.  Trying again");
                }
            }
        }
        this.logger.log(Level.SEVERE,"Exiting JRF.run()");
    }
}
