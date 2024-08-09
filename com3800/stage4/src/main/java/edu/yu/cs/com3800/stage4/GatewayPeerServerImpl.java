package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.ZooKeeperLeaderElection;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class GatewayPeerServerImpl extends ZooKeeperPeerServerImpl{
    private boolean knowCurrentLeader=false;
    public GatewayPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress) {
        super(myPort, peerEpoch, id, peerIDtoAddress,id);
        super.setPeerState(ServerState.OBSERVER);
    }
    public void setPeerState(ServerState newState) {
        throw new UnsupportedOperationException("can't change the state of the server");
    }

}
