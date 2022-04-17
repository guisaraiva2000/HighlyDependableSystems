package pt.tecnico.bank.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.bank.server.domain.ServerBackend;
import pt.tecnico.bank.server.domain.adeb.AdebServiceImpl;
import sun.misc.Signal;

import java.util.Scanner;


public class ServerMain {

	public static void main(String[] args) {
		System.out.println(ServerMain.class.getSimpleName());

		String sName = args[0];
		int port = Integer.parseInt(args[1]);
		int nByzantineServers = Integer.parseInt(args[2]);

		try {

			ServerBackend serverBackend = new ServerBackend(sName, nByzantineServers);

			final BindableService impl = new ServerServiceImpl(serverBackend);

			// Create a new server to listen on port
			Server server = ServerBuilder.forPort(port)
					.addService(new ServerServiceImpl(serverBackend))
					.addService(new AdebServiceImpl(serverBackend)).build();

			// Start the server
			server.start();

			// Server threads are running in the background.
			System.out.println(sName + " started on port: " + port);

			// Create new thread where we wait for the user input.
			new Thread(() -> {
				System.out.println("<Press enter to shutdown>");
				new Scanner(System.in).nextLine();

				server.shutdown();
			}).start();

			// Catch SIGINT signal
			Signal.handle(new Signal("INT"), signal -> server.shutdown());

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();

		} catch (Exception e) {
			System.out.println("Internal Server Error: " + e.getMessage());
		} finally {
			System.out.println("Server closed");
			System.exit(0);
		}
	}
}
