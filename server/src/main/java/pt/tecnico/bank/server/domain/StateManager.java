package pt.tecnico.bank.server.domain;

import java.io.*;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;

public class StateManager {

    private final Path dataPath;
    private final String sName;

    public StateManager(String sName) {
        this.sName = sName;
        dataPath = Paths.get(System.getProperty("user.dir"), "storage", this.sName, "data.txt");
    }

    ConcurrentHashMap<PublicKey, User> loadState() {
        ConcurrentHashMap<PublicKey, User> users = new ConcurrentHashMap<>();
        try (FileInputStream fis = new FileInputStream(dataPath.toString());
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            users = (ConcurrentHashMap<PublicKey, User>) ois.readObject();
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

    void saveState(ConcurrentHashMap<PublicKey, User> users) {
        try {
            byte[] userBytes = mapToBytes(users);

            Path tmpPath = Paths.get(System.getProperty("user.dir"), "storage", this.sName);
            Path tmpPathFile = File.createTempFile("atomic", "tmp", new File(tmpPath.toString())).toPath();
            Files.write(tmpPathFile, userBytes, StandardOpenOption.APPEND);

            Files.move(tmpPathFile, dataPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    byte[] mapToBytes(ConcurrentHashMap<PublicKey, User> users) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            out.writeObject(users);
            byte[] userBytes = byteOut.toByteArray();
            out.flush();
            byteOut.close();
            return userBytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
