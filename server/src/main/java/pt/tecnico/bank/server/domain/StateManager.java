package pt.tecnico.bank.server.domain;

import java.io.*;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.HashMap;

public class StateManager {

    private final Path dataPath;

    public StateManager(int id) {
        dataPath = Paths.get(System.getProperty("user.dir"), "storage", "server_" + id, "data.txt");
    }

    HashMap<PublicKey, User> loadState() {
        HashMap<PublicKey, User> users = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(dataPath.toString());
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            users = (HashMap<PublicKey, User>) ois.readObject();
        } catch (FileNotFoundException fnfe) {
            try {
                Files.createDirectories(dataPath.getParent());
                Files.createFile(dataPath);
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

        Files.move(tmpPathFile, dataPath, StandardCopyOption.ATOMIC_MOVE);
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
