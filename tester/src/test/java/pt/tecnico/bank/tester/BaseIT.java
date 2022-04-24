package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.ServerServiceImpl;
import pt.tecnico.bank.server.domain.ServerBackend;
import pt.tecnico.bank.server.domain.adeb.AdebServiceImpl;
import pt.tecnico.bank.server.grpc.Server.OpenAccountRequest;

import java.io.IOException;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class BaseIT {

	protected static ByzantineClientServerFrontend bcsFrontend;
	private static Server server;
	protected static final Map<String, Crypto> cryptos = new HashMap<>();

	@BeforeAll
	public static void oneTimeSetup() throws IOException {
		int port = 8080;

		bcsFrontend = new ByzantineClientServerFrontend(port);

		ServerBackend serverBackend = new ServerBackend("Server1", 0);

		server = ServerBuilder.forPort(port)
				.addService(new ServerServiceImpl(serverBackend))
				.addService(new AdebServiceImpl(serverBackend)).build();

		server.start();
	}

	public static void openUser(String username, String password) {

		cryptos.put(username, new Crypto(username, password, true));

		Key pubKey = cryptos.get(username).generateKeyStore(username);
		byte[] encoded = pubKey.getEncoded();
		int initWid = 0;
		int initBalance = 100;
		byte[] pairSignature = cryptos.get(username).encrypt(username, initWid + String.valueOf(initBalance));
		String m = username + initWid + initBalance + Arrays.toString(pairSignature) + pubKey;
		byte[] signature = cryptos.get(username).encrypt(username, m);

		OpenAccountRequest req = OpenAccountRequest.newBuilder()
				.setUsername(username)
				.setInitWid(initWid)
				.setInitBalance(initBalance)
				.setPairSignature(ByteString.copyFrom(pairSignature))
				.setPublicKey(ByteString.copyFrom(encoded))
				.setSignature(ByteString.copyFrom(signature))
				.build();

		bcsFrontend.openAccount(req);
	}

	@AfterAll
	public static void cleanup() {
		bcsFrontend.close();
		server.shutdown();
	}

}
