package pt.tecnico.bank.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import sun.misc.Signal;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Scanner;


public class ServerMain {

	public static void main(String[] args) {
		System.out.println(ServerMain.class.getSimpleName());

		int port = 8080;

		while(!isPortAvailable(port)) {
			if (++port == 8083) {
				System.out.println("Error: Exceeded number of servers.");
				System.out.println("Closing...");
				System.exit(-1);
			}
		}

		try {
			final BindableService impl = new ServerServiceImpl(port);

			// Create a new server to listen on port
			Server server = ServerBuilder.forPort(port).addService(impl).build();

			// Start the server
			server.start();

			// Server threads are running in the background.
			System.out.println("Server started on port: " + port);

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

	/**
	 * Check to see if a port is available.
	 *
	 * @param port - the port to check for availability.
	 */
	public static boolean isPortAvailable(int port) {
		try (var ss = new ServerSocket(port); var ds = new DatagramSocket(port)) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
