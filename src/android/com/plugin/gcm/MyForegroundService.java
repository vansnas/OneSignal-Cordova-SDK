package com.plugin.gcm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;

public class MyForegroundService extends Service {

    private static final String TAG = "Settings and Support App";
    private static final String CHANNELID = "Foreground Service ID";
    private static final NotificationChannel channel = new NotificationChannel(
            CHANNELID,
            CHANNELID,
            NotificationManager.IMPORTANCE_LOW
    );
    private Process process = null;
    private BufferedReader reader = null;
    private File logFile = null;
    private BufferedWriter writer = null;

    @Override
    public void onCreate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        if (reader == null || !isProcessAlive(process)) {
                            process = startLogcatProcess();
                            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        }

                        logFile = generateLogFile();
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
                            readLinesFromLog(writer);
                        }

                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error starting logcat process", e);
                } finally {
                    cleanupResources();
                }
            }
        }).start();
        super.onCreate();
    }

    private void cleanupResources() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close reader", e);
            }
            reader = null;
        }

        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private void readLinesFromLog(BufferedWriter writer) throws IOException {
        String line;
        int countLines = 0;

        while ((line = reader.readLine()) != null) {
            if (!isCurrentLogFile(logFile)) {
                writer.close();
                logFile = generateLogFile();
                writer = new BufferedWriter(new FileWriter(logFile));
            }

            writeLineToLog(writer, line);
            countLines++;
            logNumberOflines(countLines);
        }
    }

    private void writeLineToLog(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = getApplicationContext();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int appIconResId = applicationInfo.icon;
        
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(this, CHANNELID)
                .setContentText("Service is running")
                .setContentTitle("Settings and Support App")
                .setSmallIcon(appIconResId);

        startForeground(1001, notification.build());
        
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //Creates the file name with the current date.
    //If there is already a file with that name, the filename will include date and time.
    public String createFileName() throws IOException {
        String filename = "logcat_" + LocalDate.now() + ".txt";
        File file = new File(getFilesDir(), filename);

        if (file.exists()) {

            LocalDateTime dateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm");
            String formattedDateTime = dateTime.format(formatter);

            filename = "logcat_" + formattedDateTime + ".txt";

        }
        return filename;
    }

    //Checks if the log file name contains the current date
    private boolean isCurrentLogFile(File logFile) {
        return logFile.getName().contains(LocalDate.now().toString());
    }

    public File generateLogFile() throws IOException {
        String filename = createFileName();
        Log.i(TAG, "New Logfile Created: " + filename);
        return new File(getFilesDir(), filename);
    }

    //After every 100 lines, the count of lines written to the log file will be logged.
    public void logNumberOflines(Integer countLines) {
        if (countLines % 100 == 0) {
            Log.i(TAG, countLines + " number of lines written");
        }
    }

    private Process startLogcatProcess() throws IOException {
        return Runtime.getRuntime().exec("logcat");
        //Report the process id of the process created
    }

    private boolean isProcessAlive(Process process) {
        return process != null && process.isAlive();
    }

    //Writes the log to the file
    private void writeLineToLog(String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) { //close the writer and set it to null
            Log.e(TAG, "Failed to write line to log: " + line, e);
        }
    }

    //Reads the lines from the logcat and write it down to the respective log file
    private void readLinesFromLog(){

        String line;
        int countLines = 0;

        try {

            //Verifies if the line has a log
            while ((line = reader.readLine()) != null) {

                //what happen when read line returns null? (line 63)

                //Checks if the log file is the required one.
                //If it's not the same day, closes the current writer, creates a new log file and initializes a new writer
                if (!isCurrentLogFile(logFile)) {
                    writer.close();
                    writer = null;
                    logFile = generateLogFile();
                    writer = new BufferedWriter(new FileWriter(logFile));
                    Log.i(TAG, "New Logfile Created");
                }

                //what happen when writer fails? (line 77)
                writeLineToLog(line);
                writer.flush();

                countLines += 1;
                logNumberOflines(countLines);
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to read logcat or write to file", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close writer", e);
                }
                writer = null;
            }
        }
    }

}
