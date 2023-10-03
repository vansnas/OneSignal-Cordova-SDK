package com.plugin.gcm;

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
    private static List<String> filesList = new ArrayList<>();
    private FileOutputStream fos = null;
    private static ZipOutputStream zos = null;

    //Generates the zip file and uploads it to blob storage
    public void generateZipFile(Context context, String VIN, String ClientId, String ClientSecret, String TennantId, String Scope, String URL){
        new Thread(new Runnable() {
            @Override
            public void run() {
                listFilesOfDirectory(context);
                String filepath = createZipFile(context, VIN);
                if(filepath != null) {
                    for (String file : filesList) {
                        addFileToZip(file);
                    }
                    closeZipFile();
                    uploadFileToBlob(filepath, ClientId, ClientSecret, TennantId, Scope, URL);
                }
            }
        }).start();
    }


    //Creates the zip file that will contain the logcat files
    private String createZipFile(Context context, String vin){

        File appDataDir = context.getFilesDir();
        String appDataPath = appDataDir.getAbsolutePath();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String dateTimeFormated = LocalDateTime.now().format(formatter);
        String zipname = appDataPath + "/logcat_" + vin + "_" + dateTimeFormated + ".zip";

        try {
            fos = new FileOutputStream(zipname);
            zos = new ZipOutputStream(fos);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to open FileOutputStream", e);
            return null;
        }
        return zipname;
    }

    //Lists the files of the current directory and add them to the list of files
    private static void listFilesOfDirectory(Context context){

        File directory = context.getFilesDir();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.isFile() & file.getPath().toLowerCase().endsWith(".txt")) {
                    filesList.add(context.getFilesDir()+ "/" + file.getName());
                }
            }
        }
    }

    //Adds a specific logact text file to the zip file
    private static void addFileToZip(String filename){
        try {
            File file = new File(filename);

            FileInputStream fis = new FileInputStream(file);

            ZipEntry zipEntry = new ZipEntry(file.getName());
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[16000];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }

            fis.close();
            zos.closeEntry();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to add file '" + filename + "' to zip", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to add new entry to zip file", e);
        }
    }

    //Closes the file and zip output streams of the zip file
    private void closeZipFile(){
        if(zos != null) {
            try {
                zos.close();
            }catch (IOException e) {
                Log.e(TAG, "Failed to close ZipOutputStream", e);
            }
            zos = null;
        }
        if(fos != null) {
            try {
                fos.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close FileOutputStream", e);
            }
            fos = null;
        }
    }

    //Uploads the zip file to the blob storage
    private static void uploadFileToBlob(String filename, String ClientId, String ClientSecret, String TennantId, String Scope, String URL){
        File file = new File(filename);
        if (file.exists()) {
            new MicrosoftAzureStorageConnection().uploadZipFile(filename, ClientId, ClientSecret, TennantId, Scope, URL);
        } else {
            Log.e(TAG, "Logcat file not found");
        }
    }

}

