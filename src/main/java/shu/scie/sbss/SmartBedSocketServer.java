package shu.scie.sbss;

/**
 * Created by Thorn on 2017/3/11.
 */
public class SmartBedSocketServer {
    public static void main(String[] args){
        SocketServer socketServer = new SocketServer();
        socketServer.printLog("server start");
        socketServer.start();
    }
}
