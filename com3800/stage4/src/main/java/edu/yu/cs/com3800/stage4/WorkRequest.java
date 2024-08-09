package edu.yu.cs.com3800.stage4;

public class WorkRequest {
    private long requestID;
    private byte[] contents;
    public WorkRequest(long requestID,byte[] contents){
        this.requestID=requestID;
        this.contents=contents;
    }
    public long getRequestID(){return this.requestID;}
    public byte[] getContents(){return this.contents;}
}
