package shu.scie.sbss;

import org.json.JSONObject;
import shu.scie.sbss.shu.scie.sbss.model.BedSocket;
import shu.scie.sbss.shu.scie.sbss.model.ControlSocket;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SocketServer {
	private static final int PORT = 4900;
	List<BedSocket> bedList = new ArrayList<BedSocket>();
	List<ControlSocket> controlList = new ArrayList<ControlSocket>();
	ServerSocket serverSocket = null;
	BufferedReader reader = null;
	
	public static void main(String[] args){
		SocketServer socketServer = new SocketServer();
		socketServer.start();
	}

	public void start(){
		try {
			serverSocket = new ServerSocket(PORT);
			Socket socket;
			while(true){
				socket = serverSocket.accept();
				printLog(socket.hashCode()+" connect");
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String type = reader.readLine();
				printLog("type: " + type);
				if(type.equals("bed")){
					String id = reader.readLine();
					BedSocket bedSocket=new BedSocket();
					bedSocket.setId(id);
					bedSocket.setSocket(socket);
					if(!bedList.contains(bedSocket)){
						bedList.add(bedSocket);
						bedConnection(bedSocket);
					}

				}else {
					String id = reader.readLine();
					ControlSocket controlSocket = new ControlSocket();
					controlSocket.setId(id);
					controlSocket.setSocket(socket);
					if(!controlList.contains(controlSocket)){
						controlList.add(controlSocket);
						ControlConnection(controlSocket);
					}

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void ControlConnection(final ControlSocket controlSocket){
		new Thread(new Runnable() {
			public void run() {
				printLog("controller " + controlSocket.getId() + "'s connection start");
				BufferedReader controllerReader = null;
				BufferedWriter controllerWriter = null;
				BufferedWriter targetWriter = null;
				BedSocket targetBed = null;
				checkHeartBeat(controlSocket.getSocket());
				try {
					String command;
					controllerReader = new BufferedReader(new InputStreamReader(controlSocket.getSocket().getInputStream()));
					controllerWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getSocket().getOutputStream()));
					controlSocket.getSocket().setSoTimeout(10000);
					while ((command = controllerReader.readLine()) != null && !controlSocket.getSocket().isClosed()){
						JSONObject jsonObject = new JSONObject(command);
						String type = jsonObject.getString("type");
						if(type.equals("target")){
							String targetID = jsonObject.getString("msg");
							String user = jsonObject.getString("user");
							controlSocket.setTargetID(targetID);
							for(BedSocket bedSocket : bedList){
								if (bedSocket.getId().equals(targetID)){
									targetBed = bedSocket;
									targetWriter = new BufferedWriter(new OutputStreamWriter(targetBed.getSocket().getOutputStream()));
									//此处告知床应登录的id
									JSONObject jsonObject2Bed = new JSONObject();
									jsonObject2Bed.put("type", "user");
									jsonObject2Bed.put("msg", user);
									targetWriter.write(jsonObject2Bed.toString() + '\n');
									targetWriter.flush();
									break;
								}
							}
							if(targetBed == null){
								printLog("not found target bed " + targetID);
							}
						} else if(type.equals("command")){
							String action = jsonObject.getString("msg");

							if(targetBed != null && !targetBed.getSocket().isClosed()){
								try {
									JSONObject jsonObject2Bed = new JSONObject();
									jsonObject2Bed.put("type", "action");
									jsonObject2Bed.put("msg", action);
									targetWriter.write(jsonObject2Bed.toString() + "\n");
									targetWriter.flush();
								} catch (Exception targetE){
									bedList.remove(targetBed);
									if (!targetBed.getSocket().isClosed()){
										printLog("close socket " + targetBed.getSocket().hashCode());
										targetBed.getSocket().close();
										printLog("bedList's size is " + bedList.size());
									}
								}
							}

						} else {

						}
					}
				} catch (Exception e) {
					printLog(controlSocket.getId()+" lost");
				} finally {
					controlList.remove(controlSocket);
					try {
						if(!controlSocket.getSocket().isClosed())
							controlSocket.close();
					} catch (Exception e){
						printLog(e.getMessage());
					}
				}
			}
		}).start();
	}

	public void bedConnection(final BedSocket bedSocket){
		new Thread(new Runnable() {
			public void run() {
				printLog("bed " + bedSocket.getId() + "'s connection start");
				BufferedWriter bedWriter;
				BufferedReader bedReader;
				String response;
				checkHeartBeat(bedSocket.getSocket());
				try {
					bedWriter = new BufferedWriter(new OutputStreamWriter(bedSocket.getSocket().getOutputStream()));
					bedReader = new BufferedReader(new InputStreamReader(bedSocket.getSocket().getInputStream()));
					bedSocket.getSocket().setSoTimeout(10000);
					while (true){
						if(!bedSocket.getSocket().isClosed() && (response = bedReader.readLine()) != null){
							JSONObject jsonObject = new JSONObject(response);
							String type = jsonObject.getString("type");
							if(type.equals("heart beat")){

							} else {
								for (ControlSocket controlSocket : controlList){
									if(controlSocket.getTargetID().equals(bedSocket.getId())){
										try {
											BufferedWriter targetWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getSocket().getOutputStream()));
											JSONObject jsonObject2Ctrl = new JSONObject();
											jsonObject2Ctrl.put("type", "action");
											jsonObject2Ctrl.put("msg", jsonObject.getString("msg"));
											targetWriter.write(jsonObject2Ctrl.toString() + "\n");
											targetWriter.flush();
											targetWriter.close();
										} catch (IOException e) {
											printLog("bed " + bedSocket.getId() + " write to controller " + controlSocket.getId() + " meet error");
											JSONObject jsonObjectReturn = new JSONObject();
											jsonObjectReturn.put("type", "error");
											jsonObjectReturn.put("msg", "send to target controller error!");
											bedWriter.write(jsonObjectReturn.toString() + "\n");
											bedWriter.flush();
										}
									}
								}
								System.out.println(response);
							}
						} else {
							printLog(bedSocket.getId() + "closed");
							break;
						}
					}
				} catch (Exception e) {
					printLog("bed: " + bedSocket.getId() + " error");
					printLog("error: " + e.getMessage());
				} finally {
					try {
						bedList.remove(bedSocket);
						if (!bedSocket.getSocket().isClosed()){
							printLog("close socket " + bedSocket.getSocket().hashCode());
							bedSocket.getSocket().close();
							printLog("bedList's size is " + bedList.size());
						}
					} catch (IOException e1) {
						printLog("error1: " + e1.getMessage());
					}
				}

			}
		}).start();
	}

	public void checkHeartBeat(final Socket socket){
		new Thread(new Runnable() {
			public void run() {
				printLog("socket " + socket.hashCode() + "'s check thread");
				try {
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
					while (true) {
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("type", "check heart beat");
						writer.write(jsonObject.toString() + '\n');
						writer.flush();
						Thread.sleep(5000);
					}
				} catch (Exception e){
					printLog("socket " + socket.hashCode() + " meet error: " + e.getMessage());
				}
			}
		}).start();
	}

	public void printLog(String log){
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		System.out.println(timestamp + " : " + log);
	}

}