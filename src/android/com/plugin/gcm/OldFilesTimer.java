package com.plugin.gcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class OldFilesTimer extends BroadcastReceiver {
    
    private static List<String> filesList = new ArrayList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
            // Execute file deletion on a background thread
            new Thread(() -> deleteOldFiles(context)).start();
        }
    }

    public static void deleteOldFiles(Context context) {
        listFilesOfDirectory(context);
        for (String filename : filesList) {
            if (!deleteFile(filename)) {
                Log.e("OldFilesTimer", "Failed to delete file: " + filename);
            }
        }
    }

    //List all the files in the directory and add the right ones to the list of files
    private static void listFilesOfDirectory(Context context){
        File directory = context.getFilesDir();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.isFile()) {
                    filterFiles(file, context);
                }
            }
        }
    }

    //Deletes the given file
    private static boolean deleteFile(String filename) {
        File file = new File(filename);
        return file.exists() && file.delete();
}

    //Filters the list of files and add the respective ones to the list
    private static void filterFiles(File file, Context context){
        if(file.getName().contains("logcat") && file.getName().contains(".txt")) {
            LocalDate date = LocalDate.now().minusDays(7);
            if (!getDateFromFilename(file.getName()).isAfter(date)) {
                filesList.add(context.getFilesDir() + "/" + file.getName());
            }
        } else if (file.getName().contains("logcat") && file.getName().contains(".zip")) {
            filesList.add(context.getFilesDir() + "/" + file.getName());
        }
    }

    //Returns the date of the logcat filename
    public static LocalDate getDateFromFilename(String filename){
        String getDate = filename.replace("logcat_", "").replace(".txt", "");
        LocalDate logcatDate;
        if(getDate.contains("T")){
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm");;
            LocalDateTime logcatDateTime = LocalDateTime.parse(getDate, dateTimeFormatter);
            logcatDate = logcatDateTime.toLocalDate();
        }else{
            DateTimeFormatter localDate = DateTimeFormatter.ISO_LOCAL_DATE;
            logcatDate = LocalDate.parse(getDate, localDate);
        }
        return logcatDate;
    }

}
