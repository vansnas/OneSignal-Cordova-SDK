package org.apache.cordova.logcat;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogcatHistoryFile {

    private static final String TAG = "GenerateZipFile";
    private FileOutputStream fos = null;
    private ZipOutputStream zos = null;

    //Generates the zip file and uploads it to blob storage
    public void generateZipFile(Context context, String VIN, String ClientId, String ClientSecret, String TennantId) {
        new Thread(() -> {
            List<String> filesList = listFilesOfDirectory(context);
            if (filesList.isEmpty()) {
                Log.e(TAG, "No logcat files found for zipping.");
                return;
            }

            String filepath = createZipFile(context, VIN);
            if (filepath != null) {
                try {
                    for (String file : filesList) {
                        addFileToZip(file);
                    }
                    closeZipFile();
                    uploadFileToBlob(filepath, ClientId, ClientSecret, TennantId);
                } catch (IOException e) {
                    Log.e(TAG, "Error generating zip file", e);
                } finally {
                    closeZipFile(); // Ensure zip is closed even on failure
                }
            }
        }).start();
    }

    // Creates the zip file that will contain the logcat files
    private String createZipFile(Context context, String vin) {
        File appDataDir = context.getFilesDir();
        String appDataPath = appDataDir.getAbsolutePath();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String dateTimeFormatted = LocalDateTime.now().format(formatter);
        String zipname = appDataPath + "/logcat_" + vin + "_" + dateTimeFormatted + ".zip";

        try {
            fos = new FileOutputStream(zipname);
            zos = new ZipOutputStream(fos);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to open FileOutputStream", e);
            return null;
        }
        return zipname;
    }

    // Lists the files of the current directory and adds them to the list of files
    private List<String> listFilesOfDirectory(Context context) {
        List<String> filesList = new ArrayList<>();
        File directory = context.getFilesDir();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".txt")) {
                        filesList.add(file.getAbsolutePath());
                    }
                }
            }
        }
        return filesList;
    }

    // Adds a specific logcat text file to the zip file
    private void addFileToZip(String filename) throws IOException {
        File file = new File(filename);
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[16000];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }

            zos.closeEntry();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to add file '" + filename + "' to zip", e);
        }
    }

    // Closes the file and zip output streams of the zip file
    private void closeZipFile() {
        if (zos != null) {
            try {
                zos.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close ZipOutputStream", e);
            }
            zos = null;
        }
        if (fos != null) {
            try {
                fos.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close FileOutputStream", e);
            }
            fos = null;
        }
    }

    // Uploads the zip file to blob storage
    private static void uploadFileToBlob(String filename, String ClientId, String ClientSecret, String TennantId) {
        File file = new File(filename);
        if (file.exists()) {
            new MicrosoftAzureStorageConnection().uploadZipFile(filename, ClientId, ClientSecret, TennantId);
        } else {
            Log.e(TAG, "Logcat file not found");
        }
    }
}
