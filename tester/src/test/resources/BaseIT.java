import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

import org.junit.jupiter.api.*;


public class BaseIT {

	private static final String TEST_PROP_FILE = "/test.properties";
	protected static Properties testProps;

	@BeforeAll
	public static void oneTimeSetup () throws IOException {
		testProps = new Properties();

		try {
			testProps.load(BaseIT.class.getResourceAsStream(TEST_PROP_FILE));
			System.out.println("Test properties:");
			System.out.println(testProps);
		}catch (IOException e) {
			final String msg = String.format("Could not load properties file {}", TEST_PROP_FILE);
			System.out.println(msg);
			throw e;
		}
	}
	
	@AfterAll
	public static void cleanup() {
        File dir = Paths.get(System.getProperty("user.dir") + File.separator + "CERTIFICATES" + File.separator).toFile();
		for(File file: Objects.requireNonNull(dir.listFiles()))
			if (!file.isDirectory() && !file.getName().equals("server.cert"))
				file.delete();
	}

}
