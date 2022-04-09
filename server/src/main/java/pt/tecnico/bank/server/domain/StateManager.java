package pt.tecnico.bank.server.domain;

import java.io.*;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.HashMap;

public class StateManager {

    private final Path DATA_PATH = Paths.get(System.getProperty("user.dir"), "storage", "data.txt");

    public StateManager() {
    }

    HashMap<PublicKey, User> loadState() {
        HashMap<PublicKey, User> users = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(DATA_PATH.toString());
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            users = (HashMap<PublicKey, User>) ois.readObject();
        } catch (FileNotFoundException fnfe) {
            try {
                Files.createDirectories(DATA_PATH.getParent());
                Files.createFile(DATA_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (EOFException e) {
            System.out.println("Warning: Database is empty");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return users;
    }

    void saveState(HashMap<PublicKey, User> users) throws IOException {
        byte[] userBytes = mapToBytes(users);

        Path tmpPath = Paths.get(System.getProperty("user.dir"), "storage");
        Path tmpPathFile = File.createTempFile("atomic", "tmp", new File(tmpPath.toString())).toPath();
        Files.write(tmpPathFile, userBytes, StandardOpenOption.APPEND);

        Files.move(tmpPathFile, DATA_PATH, StandardCopyOption.ATOMIC_MOVE);
    }

    byte[] mapToBytes(HashMap<PublicKey, User> users) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(byteOut);
        out.writeObject(users);
        byte[] userBytes = byteOut.toByteArray();
        out.flush();
        byteOut.close();
        return userBytes;
    }
}
