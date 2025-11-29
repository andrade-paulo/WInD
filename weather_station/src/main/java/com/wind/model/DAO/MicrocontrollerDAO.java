package com.wind.model.DAO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.wind.datastructures.Hash;
import com.wind.entities.MicrocontrollerEntity;

public class MicrocontrollerDAO {
    private Hash<MicrocontrollerEntity> microcontrollerHash;
    private int ocupacao;

    // private final String FILE_PATH = "database/microcontrollers.dat";
    private final String FILE_PATH = "/app/database/microcontrollers.dat";
    private final int INITIAL_SIZE = 100;

    public MicrocontrollerDAO() {
        microcontrollerHash = new Hash<>(INITIAL_SIZE);
        loadDatabase();
        ocupacao = microcontrollerHash.getOcupacao();
    }

    public void addMicrocontroller(MicrocontrollerEntity microcontroller) {
        if (microcontroller.getId() == 0) {
             microcontroller.setId(ocupacao + 1);
        }

        microcontrollerHash.inserir(microcontroller.getId(), microcontroller);
        ocupacao++;
        LogDAO.addLog("[MC INSERT] Microcontroller " + microcontroller.getId() + " registered.");
        updateFile();
    }

    public MicrocontrollerEntity getMicrocontroller(int id) {
        MicrocontrollerEntity mc = microcontrollerHash.buscar(id);
        if (mc == null) {
            LogDAO.addLog("[MC MISS] Microcontroller " + id + " not found.");
            return null;
        }
        LogDAO.addLog("[MC HIT] Microcontroller " + id + " found.");
        return mc;
    }

    public List<MicrocontrollerEntity> getAllMicrocontrollers() {
        LogDAO.addLog("[MC SELECT] Selecting all microcontrollers.");
        List<MicrocontrollerEntity> list = new ArrayList<>();
        for (MicrocontrollerEntity mc : microcontrollerHash) {
            if (mc != null) {
                list.add(mc);
            }
        }
        return list;
    }

    public void updateMicrocontroller(MicrocontrollerEntity microcontroller) {
        if (microcontrollerHash.buscar(microcontroller.getId()) != null) {
            microcontrollerHash.inserir(microcontroller.getId(), microcontroller);
            LogDAO.addLog("[MC UPDATE] Microcontroller " + microcontroller.getId() + " updated.");
            updateFile();
        } else {
            LogDAO.addLog("[MC MISS] Microcontroller " + microcontroller.getId() + " not found for update.");
        }
    }

    public void deleteMicrocontroller(int id) {
        try {
            microcontrollerHash.remover(id);
            ocupacao--;
            LogDAO.addLog("[MC DELETE] Microcontroller " + id + " deleted.");
            updateFile();
        } catch (Exception e) {
            LogDAO.addLog("[MC ERROR] Error deleting microcontroller " + id + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadDatabase() {
        try {
            File file = new File(FILE_PATH);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis);
                microcontrollerHash = (Hash<MicrocontrollerEntity>) ois.readObject();
                ois.close();
                fis.close();
                LogDAO.addLog("[DB LOAD] Database loaded successfully.");
            } else {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
                updateFile(); // Initialize empty file
                LogDAO.addLog("[DB INIT] New database created.");
            }
        } catch (Exception e) {
            LogDAO.addLog("[DB ERROR] Error loading database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateFile() {
        try {
            FileOutputStream fos = new FileOutputStream(FILE_PATH);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(microcontrollerHash);
            oos.close();
            fos.close();
        } catch (IOException e) {
            LogDAO.addLog("[DB ERROR] Error saving database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
