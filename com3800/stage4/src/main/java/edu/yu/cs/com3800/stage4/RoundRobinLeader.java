package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoundRobinLeader extends Thread implements LoggingServer {
    class GetWorkDone implements Runnable{
        private Logger logger;
        private InetSocketAddress myWorker;
        private Socket socketToWorker;
        private Socket socketToGateway;
        private long requestID;
        private InetSocketAddress myTCPAddress;
        public GetWorkDone(Socket socketToGateway,InetSocketAddress myWorker, InetSocketAddress myTCPAddress, long requestID){
            this.myWorker=myWorker;
            try {
                this.logger = initializeLogging("logs/Work-Logs",GetWorkDone.class.getCanonicalName() + "-using-worker-on-Port-" + this.myWorker.getPort());
            } catch (IOException e) {
                System.out.println("failed to create logger and got exception: "+e.toString());
                e.printStackTrace();
            }
            this.socketToGateway=socketToGateway;
            this.myTCPAddress=myTCPAddress;
            this.requestID=requestID;
            while(true){
                try {
                    this.socketToWorker=new Socket(myWorker.getHostName(),myWorker.getPort());
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            try (InputStream inputFromGateway=this.socketToGateway.getInputStream();
                 OutputStream outputToGateway=this.socketToGateway.getOutputStream();
                 InputStream inputFromWorker=this.socketToWorker.getInputStream();
                 OutputStream outputToWorker=this.socketToWorker.getOutputStream()){
                //getting the work to be done
                Message m=new Message(Util.readAllBytesFromNetwork(inputFromGateway));
                this.logger.log(Level.FINEST,"received the work to be done: \n"+m.toString());

                //sending that to the worker
                outputToWorker.write(new Message(Message.MessageType.WORK, m.getMessageContents(), this.myTCPAddress.getHostString(), this.myTCPAddress.getPort(), myWorker.getHostString(), myWorker.getPort(),requestID).getNetworkPayload());
                this.logger.log(Level.FINEST,"sent output to the worker");
                //receive the response from the worker
                Message completedMessage= new Message(Util.readAllBytesFromNetwork(inputFromWorker));
                this.logger.log(Level.FINEST,"received the response from the worker: \n"+completedMessage.toString());

                //send the message back to gateway
                outputToGateway.write(new Message(Message.MessageType.COMPLETED_WORK,completedMessage.getMessageContents(),this.myTCPAddress.getHostString(), this.myTCPAddress.getPort(), socketToGateway.getInetAddress().getHostName(),socketToGateway.getPort(),requestID).getNetworkPayload());
                this.logger.log(Level.FINEST,"sent result of work to the gateway server");
            } catch (IOException e) {
                this.logger.log(Level.WARNING, "hit IOException line 72: "+e.toString());
            }
            finally{
                while(true){
                    try {
                        this.socketToWorker.close();
                        this.logger.log(Level.FINEST,"closed socket to worker");
                        break;
                    } catch (IOException e) {
                        this.logger.log(Level.WARNING,"failed to close socket to worker, retrying");
                    }
                }
            }
        }
    }


    private int indexOfServerUpNext;
    private ServerSocket myServerSocket;
    private List<InetSocketAddress> followers;
    private InetSocketAddress myTCPAddress;
    private ThreadPoolExecutor tpe;
    private Logger logger;
    private long requestID=1;
    private boolean shutdown=false;
    public RoundRobinLeader(InetSocketAddress myTCPAddress, List<InetSocketAddress> followers){
        indexOfServerUpNext=0;
        this.followers=followers;
        this.myTCPAddress=myTCPAddress;
        try {
            this.logger = initializeLogging("logs/RRL-Log",RoundRobinLeader.class.getCanonicalName() + "-on-Leader-server-on-Port-" + this.myTCPAddress.getPort());
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(true) {
            try {
                this.myServerSocket = new ServerSocket(myTCPAddress.getPort());
                break;
            } catch (IOException e) {
                this.logger.warning("hit IO exception when trying to create ServerSocket.\n"+e.toString());
                e.printStackTrace();
            }
        }
        this.tpe= (ThreadPoolExecutor) Executors.newFixedThreadPool(followers.size());
    }
    public void shutdown() {
        this.logger.log(Level.SEVERE,"RRL shutting down");
        Thread.currentThread().interrupt();
        this.shutdown=true;
        this.tpe.shutdown();
        try {
            this.myServerSocket.close();
        } catch (IOException e) {
            this.logger.log(Level.SEVERE,"rrl can't shut down server socket",e);
        }
    }

    @Override
    public void run(){
        while(!Thread.currentThread().isInterrupted()){
            try {
                Socket next = myServerSocket.accept();
                InetSocketAddress nextServerUp=this.followers.get(indexOfServerUpNext++);
                if(indexOfServerUpNext==followers.size()){indexOfServerUpNext=0;}
                long idOfThisRequest=requestID++;
                this.logger.log(Level.FINEST,"received work request, sending to worker on port: "+nextServerUp.getPort()+" with requestID: "+idOfThisRequest);
                this.tpe.submit(new GetWorkDone(next, nextServerUp,this.myTCPAddress,idOfThisRequest));
            } catch (IOException e) {
                if(this.shutdown){
                    this.logger.log(Level.SEVERE,"IOException thrown because ServerSocket closed by shutdown().  Shutting down.");
                    Thread.currentThread().interrupt();
                }
                else{
                    this.logger.log(Level.WARNING,"IOException while accepting next socket, will retry. ",e);
                }
            }
        }
        this.logger.log(Level.SEVERE,"Exiting RRL.run()");
    }
}
