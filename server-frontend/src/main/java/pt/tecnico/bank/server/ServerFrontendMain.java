package pt.tecnico.bank.server;

import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.grpc.Server.*;

public class ServerFrontendMain {
	
	public static void main(String[] args) {
		System.out.println(ServerFrontendMain.class.getSimpleName());

		ServerFrontend frontend = new ServerFrontend(1);

		try {
			PingRequest request = PingRequest.newBuilder().setInput("friend").build();
			PingResponse response = frontend.ping(request);
			System.out.println(response.getOutput());
		} catch (StatusRuntimeException sre) {
			System.err.println("ERROR: " + sre.getMessage());
		}

		frontend.close();

	}
	
}
