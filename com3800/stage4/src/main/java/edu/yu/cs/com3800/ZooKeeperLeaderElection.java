package edu.yu.cs.com3800;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZooKeeperLeaderElection implements LoggingServer {
    /**
     * time to wait once we believe we've reached the end of leader election.
     */
    private final static int finalizeWait = 500;

    /**
     * Upper bound on the amount of time between two consecutive notification checks.
     * This impacts the amount of time to get the system up again after long partitions. Currently 60 seconds.
     */
    private final static int maxNotificationInterval = 60000;
    private int curNotificationInterval=2000;
    private LinkedBlockingQueue<Message> incomingMessages;
    private ZooKeeperPeerServer myPeerServer;
    private long proposedLeader;
    private long proposedEpoch=1;
    private int quorumSize;
    private Map<Long,Long> IdToNumCurrentVotes=new HashMap<>();
    private Map<Long,Long> IdToCurrentVote=new HashMap<>();
    private boolean electionEpochComplete=false;
    private Logger logger;
    public ZooKeeperLeaderElection(ZooKeeperPeerServer server, LinkedBlockingQueue<Message> incomingMessages)
    {
        this.incomingMessages = incomingMessages;
        this.myPeerServer = server;
        this.quorumSize=server.getQuorumSize();
        this.proposedLeader=server.getServerId();
        this.IdToNumCurrentVotes.put(proposedLeader,1L);
        this.IdToCurrentVote.put(proposedLeader,proposedLeader);
        try {
            this.logger = initializeLogging("logs/Election-Logs",ZooKeeperLeaderElection.class.getCanonicalName() + "-on-server-with-ID-" + this.myPeerServer.getServerId());
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.logger.log(Level.FINEST,"this.quorumSize() is: "+this.quorumSize);
    }

    private synchronized Vote getCurrentVote() {
        return new Vote(this.proposedLeader, this.proposedEpoch);
    }

    public synchronized Vote lookForLeader()
    {
        //send initial notifications to other peers to get things started
        sendNotifications();
        //Loop, exchanging notifications with other servers until we find a leader
        while ((this.myPeerServer.getPeerState() == ZooKeeperPeerServer.ServerState.LOOKING||this.myPeerServer.getPeerState()== ZooKeeperPeerServer.ServerState.OBSERVER)&&!electionEpochComplete) {
            try {
                Message m = incomingMessages.poll((curNotificationInterval), TimeUnit.MILLISECONDS);
                if (m == null) {
                    if(curNotificationInterval<maxNotificationInterval){
                        curNotificationInterval*=2;
                        if(curNotificationInterval>maxNotificationInterval){
                            curNotificationInterval=maxNotificationInterval;
                        }
                    }
                    sendNotifications();
                    continue;
                }
                this.logger.log(Level.FINEST,"\nserver: "+this.myPeerServer.getServerId()+" is processing an incoming message\n"+m.toString());
                ElectionNotification notification = getNotificationFromMessage(m);
                Vote v = processElectionNotification(notification);
                if (v != null) {
                    return v;
                }

            } catch (InterruptedException e) {
                continue;
            }
        }
        return null;
    }
    private Vote processElectionNotification(ElectionNotification notification){
        ZooKeeperPeerServer.ServerState senderState=notification.getState();
        long senderID=notification.getSenderID();
        long senderProposedLeaderID=notification.getProposedLeaderID();
        if(senderState== ZooKeeperPeerServer.ServerState.OBSERVER){
            sendNotifications();
            return null;
        }
        if(this.IdToCurrentVote.get(senderID)!=null){ //if there was previously a vote from this sender - remove the record of it
            long previousVoteOfSender=this.IdToCurrentVote.get(senderID);
            this.IdToNumCurrentVotes.replace(previousVoteOfSender,this.IdToNumCurrentVotes.get(previousVoteOfSender)-1);
        }
        //add/modify record of the vote of sender
        this.IdToCurrentVote.put(senderID,senderProposedLeaderID);
        //modify number of votes for that proposed leader
        if(this.IdToNumCurrentVotes.get(senderProposedLeaderID)!=null){
            this.IdToNumCurrentVotes.put(senderProposedLeaderID,this.IdToNumCurrentVotes.get(senderProposedLeaderID)+1);
        }
        else{
            this.IdToNumCurrentVotes.put(senderProposedLeaderID,1L);
        }
        if(supersedesCurrentVote(senderProposedLeaderID,notification.getPeerEpoch())){
            logger.log(Level.FINEST,"supersedes current vote so changing vote + sending notifications");
            this.proposedEpoch=notification.getPeerEpoch();
            this.IdToNumCurrentVotes.replace(this.proposedLeader,this.IdToNumCurrentVotes.get(this.proposedLeader)-1);
            this.proposedLeader=senderProposedLeaderID;
            this.IdToCurrentVote.put(this.proposedLeader,this.proposedLeader);
            this.IdToNumCurrentVotes.put(this.proposedLeader,this.IdToNumCurrentVotes.get(this.proposedLeader)+1);
            sendNotifications();
        }
        switch(senderState){
            case LOOKING:
                if(haveAQuorum(proposedLeader)&&noBetterVotesOnQueue()){
                    logger.log(Level.FINEST,"accepting "+proposedLeader+" as election winner");
                    return acceptElectionWinner(notification);
                }
                else {
                    return null;
                }
            case LEADING: case FOLLOWING: //ik this is the same right now, but will change once we start dealing with epochs, so left it separate
                if(haveAQuorum(proposedLeader)&&noBetterVotesOnQueue()){
                    logger.log(Level.FINEST,"accepting "+proposedLeader+" as election winner");
                    return acceptElectionWinner(notification);
                }
                else {
                    return null;
                }
            case OBSERVER:
                if(haveAQuorum(proposedLeader)&&noBetterVotesOnQueue()){
                    this.logger.log(Level.FINEST,"accepting "+proposedLeader+" as election winner");
                }
        }
        return null;
    }
    private boolean noBetterVotesOnQueue(){
        try {
            Thread.sleep(finalizeWait);
        } catch (InterruptedException e) {
            this.logger.info(Util.getStackTrace(e));
        }
        List<Message> messagesOnQueue= new ArrayList<>();
        messagesOnQueue.addAll(this.incomingMessages);
        for(Message m: messagesOnQueue){
            if(m.getMessageType()== Message.MessageType.ELECTION){
                ElectionNotification en=getNotificationFromMessage(m);
                if(en.getProposedLeaderID()>this.proposedLeader){
                    return false;
                }
            }
        }
        return true;
    }
    private void sendNotifications(){
        this.myPeerServer.sendBroadcast(Message.MessageType.ELECTION,buildMsgContent(new ElectionNotification(this.proposedLeader,this.myPeerServer.getPeerState(),this.myPeerServer.getServerId(),this.proposedEpoch)));
    }
    private Vote acceptElectionWinner(ElectionNotification n)
    {
        if(this.myPeerServer.getPeerState()== ZooKeeperPeerServer.ServerState.OBSERVER){
            logger.log(Level.FINEST,"observer watched the election and found the winner");
        }
        else{
            //set my state to either LEADING or FOLLOWING
            //clear out the incoming queue before returning
            if (this.proposedLeader==this.myPeerServer.getServerId()) {
                logger.log(Level.FINEST,"state set to leading");
                this.myPeerServer.setPeerState(ZooKeeperPeerServer.ServerState.LEADING);
            }
            else {
                logger.log(Level.FINEST,"state set to following");
                this.myPeerServer.setPeerState(ZooKeeperPeerServer.ServerState.FOLLOWING);
            }
        }
        electionEpochComplete=true;
        this.incomingMessages.clear();
        return getCurrentVote();
    }

    /*
     * We return true if one of the following three cases hold:
     * 1- New epoch is higher
     * 2- New epoch is the same as current epoch, but server id is higher.
     */
    protected boolean supersedesCurrentVote(long newId, long newEpoch) {
        return (newEpoch > this.proposedEpoch) || ((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader));
    }
    protected boolean haveAQuorum(long proposedLeaderID){
        return this.IdToNumCurrentVotes.get(proposedLeaderID)!=null&&this.IdToNumCurrentVotes.get(proposedLeaderID) >= this.quorumSize;
    }
    /**
     * Termination predicate. Given a set of votes, determines if have sufficient support for the proposal to declare the end of the election round.
     * Who voted for who isn't relevant, we only care that each server has one current vote
     */
    protected boolean haveEnoughVotes(Map<Long, ElectionNotification > votes, Vote proposal)
    {
        //is the number of votes for the proposal > the size of my peer server’s quorum?
        long count =0L;
        for(ElectionNotification e:votes.values()){
            if(e.getProposedLeaderID()==proposal.getProposedLeaderID()){count++;}
        }
        return count >= this.quorumSize;
    }
    protected static ElectionNotification getNotificationFromMessage(Message m){
        ByteBuffer buffer = ByteBuffer.wrap(m.getMessageContents());
        buffer.clear();
        long proposedLeaderID= buffer.getLong();
        char c =buffer.getChar();
        ZooKeeperPeerServer.ServerState state = ZooKeeperPeerServer.ServerState.getServerState(c);
        long senderID=buffer.getLong();
        long peerEpoch=buffer.getLong();
        return new ElectionNotification(proposedLeaderID,state,senderID,peerEpoch);
    }
    protected static byte[] buildMsgContent(ElectionNotification notification){
        //size of buffer =26
        //        1 char (msg type) = 2 bytes
        //        1 long (request ID) = 8 bytes
        ByteBuffer buffer = ByteBuffer.allocate(26);
        buffer.clear();
        buffer.putLong(notification.getProposedLeaderID());
        buffer.putChar(notification.getState().getChar());
        buffer.putLong(notification.getSenderID());
        buffer.putLong(notification.getPeerEpoch());
        buffer.flip();
        return buffer.array();
    }
}