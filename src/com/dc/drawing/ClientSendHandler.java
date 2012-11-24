package com.dc.drawing;

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientSendHandler implements Runnable {

	// Socket connection to handle.
	private ClientService service;
	private Socket socket;

	private static ObjectOutputStream out;
	
	public ClientSendHandler(ClientService service, Socket socket) {
		this.socket = socket;
		this.service = service;
		Thread t = new Thread(this);
		t.start();
	}

	@Override
	public void run() {
		try
		{
			OutputStream outStream = socket.getOutputStream();
			out = new ObjectOutputStream(outStream);

			if(!service.outgoingShapes.isEmpty()) {							
				Shape toSend = service.outgoingShapes.remove(0);
				out.writeObject(toSend);
				out.flush();
			}
			
		} catch (Exception e ) {}		
	}	
}
