package shu.scie.sbss.shu.scie.sbss.model;
import java.io.IOException;
import java.net.Socket;

public class ClientSocket {
	
	private String id;
	private Socket socket;
	
	public void setId(String id) {
		this.id=id;
	}
	
	public void setSocket(Socket socket){
		this.socket=socket;
	}
	
	public String getId(){
		return id;
	}
	
	public Socket getSocket() {
		return socket;
	}

	public void close(){
		try {
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
