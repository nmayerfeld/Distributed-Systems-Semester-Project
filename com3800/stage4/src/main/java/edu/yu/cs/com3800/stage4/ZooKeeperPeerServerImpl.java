package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ZooKeeperPeerServerImpl extends Thread implements ZooKeeperPeerServer{
    private final InetSocketAddress myUDPAddress;
    private final InetSocketAddress myTCPAddress;
    private final int myUDPPort;
    private final int myTCPPort;
    private ServerState state;
    private volatile boolean shutdown;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    private Long id;
    private Logger logger;
    private long peerEpoch;
    private volatile Vote currentLeader;
    private Map<Long,InetSocketAddress> peerIDtoUDPAddress;
    private Map<Long,InetSocketAddress> peerIDtoTCPAddress;
    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;
    private RoundRobinLeader rrl;
    private JavaRunnerFollower jrf;
    private long gatewayID;

    public ZooKeeperPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long,InetSocketAddress> peerIDtoUDPAddress, long gatewayID){
        //code here...
        this.myUDPAddress=new InetSocketAddress("localhost",myPort);
        this.myTCPAddress=new InetSocketAddress("localhost",myPort+2);
        this.myUDPPort=myPort;
        this.myTCPPort=myPort+2;
        this.peerEpoch=peerEpoch;
        this.id=id;
        this.gatewayID=gatewayID;
        this.peerIDtoUDPAddress=peerIDtoUDPAddress;
        this.peerIDtoTCPAddress=new HashMap<>();
        for(Long l:peerIDtoUDPAddress.keySet()){
            if(l!=this.gatewayID) {
                InetSocketAddress UDPAddress=peerIDtoUDPAddress.get(l);
                peerIDtoTCPAddress.put(l,new InetSocketAddress(UDPAddress.getHostString(),UDPAddress.getPort()+2));
            }
        }
        this.state=ServerState.LOOKING;
        this.outgoingMessages=new LinkedBlockingQueue<>();
        this.incomingMessages=new LinkedBlockingQueue<>();
        this.senderWorker=new UDPMessageSender(this.outgoingMessages,this.myUDPPort);
        try {
            this.logger = initializeLogging("logs/ZKPSI-Logs",ZooKeeperPeerServer.class.getCanonicalName() + "-on-server-with-ID-" + this.id);
            this.receiverWorker=new UDPMessageReceiver(this.incomingMessages,this.myUDPAddress,this.myUDPPort,this);
        } catch (IOException e) {
            this.logger.log(Level.SEVERE,"failed to create receiverWorker", e);
        }
        this.setName("ZKPSI-ID-"+this.id);
    }

    @Override
    public void shutdown(){
        this.shutdown = true;
        if(rrl!=null){
            rrl.shutdown();
        }
        if(jrf!=null){
            jrf.shutdown();
        }
        this.senderWorker.shutdown();
        this.receiverWorker.shutdown();
    }

    @Override
    public synchronized void setCurrentLeader(Vote v){
        this.currentLeader=v;
    }

    @Override
    public synchronized Vote getCurrentLeader() {
        return this.currentLeader;
    }

    @Override
    public void sendMessage(Message.MessageType type, byte[] messageContents, InetSocketAddress target) throws IllegalArgumentException {
        try {
            this.outgoingMessages.put(new Message(type, messageContents, myUDPAddress.getHostString(), myUDPPort, target.getHostString(), target.getPort()));
        } catch (InterruptedException e) {
            this.logger.log(Level.WARNING, "Exception caught while trying to add message to outgoing that sending to: "+target.getHostString(), e);
        }
    }

    @Override
    public void sendBroadcast(Message.MessageType type, byte[] messageContents) {
        for(InetSocketAddress i:this.peerIDtoUDPAddress.values()){
            if(i!=myUDPAddress){
                sendMessage(type,messageContents,i);
            }
        }
    }

    @Override
    public ServerState getPeerState() {
        return this.state;
    }

    @Override
    public void setPeerState(ServerState newState) {
        this.state=newState;
    }

    @Override
    public Long getServerId() {
        return this.id;
    }

    @Override
    public long getPeerEpoch() {
        return this.peerEpoch;
    }

    @Override
    public InetSocketAddress getAddress() {
        return this.myUDPAddress;
    }

    @Override
    public int getUdpPort() {
        return this.myUDPPort;
    }

    @Override
    public InetSocketAddress getPeerByID(long peerId) {
        return this.peerIDtoUDPAddress.get(peerId);
    }

    @Override
    public int getQuorumSize() {
        return this.peerIDtoUDPAddress.values().size()/2+1;
    }

    public int getNumWorkers(){return this.peerIDtoUDPAddress.keySet().size()-1;}

    @Override
    public void run(){
        //step 1: create and run thread that sends broadcast messages
        senderWorker.start();
        receiverWorker.start();
        ZooKeeperLeaderElection election=new ZooKeeperLeaderElection(this,this.incomingMessages);
        //step 2: create and run thread that listens for messages sent to this server
        //step 3: main server loop
        try{
            while (!this.shutdown){
                if(getPeerState()==ServerState.LOOKING||(getPeerState()==ServerState.OBSERVER&&currentLeader==null)){
                    this.setCurrentLeader(election.lookForLeader());
                }
                else if(getPeerState()==ServerState.FOLLOWING){
                    jrf=new JavaRunnerFollower(this.myTCPAddress);
                    this.logger.log(Level.FINEST,"created new JavaRunnerFollower");
                    jrf.setDaemon(true);
                    jrf.setName("JRF-ID-"+id);
                    jrf.start();
                    jrf.join();
                }
                else if(getPeerState()==ServerState.LEADING){
                    List<InetSocketAddress> otherServers=new ArrayList<>(this.peerIDtoTCPAddress.values());
                    otherServers.remove(myTCPAddress);
                    this.logger.log(Level.FINEST,"created new RoundRobinLeader");
                    rrl = new RoundRobinLeader(this.myTCPAddress,otherServers);
                    rrl.setDaemon(true);
                    rrl.setName("RRL-ID-"+id);
                    rrl.start();
                    rrl.join();
                }
                else if(getPeerState()==ServerState.OBSERVER){
                    try{
                        Thread.sleep(1000);
                    } catch(InterruptedException e){
                        this.logger.log(Level.WARNING,"Observer interrupted while sleeping.  Shutting down");
                        this.shutdown();
                    }
                }
            }
        }
        catch (InterruptedException ex) {
            this.logger.log(Level.SEVERE, "JRF or RRL were interrupted while running.  Shutting down the PeerServerImpl");
            this.shutdown();
        }
    }

}