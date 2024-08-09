package edu.yu.cs.com3800.stage1;

import java.io.IOException;

public class MyClass {
    public static void main (String[] args){
        SimpleServerImpl s=null;
        try {
            s=new SimpleServerImpl(9000);
            s.start();
        } catch (Exception e) {
            e.printStackTrace();
            s.stop();
        }
    }
}
