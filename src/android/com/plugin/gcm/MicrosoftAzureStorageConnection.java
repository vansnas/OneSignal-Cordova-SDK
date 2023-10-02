package com.plugin.gcm;

import android.util.Log;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class MicrosoftAzureStorageConnection {

    private static final String TAG = "MicrosoftAzureStorageConnection";
    private static final String grantType = "client_credentials";
    private static String clientId;
    private static String clientSecret;
    private static String scope;
    private static final String tennantId = "0c0d142b-bb72-4eb8-a9c5-49b7cc8466af";
    private static BufferedReader reader;
    private static final int CHUNK_SIZE = 1024 * 1024 * 4;
    private static OkHttpClient client = new OkHttpClient.Builder()
            .build();

    //

    public void uploadZipFile(String filepath){
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(filepath);
                if (file.exists()) {
                    String token = getAccessToken();
                    String accessBlobToken = processToken(token);
                    uploadFileResumable(accessBlobToken, file);
                } else {
                    Log.e(TAG, "File " + file.getName() + " not found (upload to Blob storage)");
                }
            }
        }).start();
    }

    class TokenResponse {
        private String access_token;

        public String getAccessToken() {
            return access_token;
        }
    }

    //Action that will upload the file to the blob
    public static void uploadFileResumable(String bearerToken, File file) {

        // Creates a multipart request body
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("application/zip"), file))
                .build();

        // Build request with required headers
        Request request = new Request.Builder()
                .url("https://stdaflogs.blob.core.windows.net/logcat/" + file.getName())
                .addHeader("Authorization", "Bearer " + bearerToken)
                .addHeader("x-ms-version", "2020-12-06")
                .addHeader("x-ms-date", getXMsDate())
                .addHeader("x-ms-blob-type", "BlockBlob")
                .put(body)
                .build();

        try {
            // Execute request to upload file
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                Log.e(TAG, "Error uploading the file, resuming...");

                if (response.code() == 308) {
                    handle308Status(response, file);
                } else {
                    Log.e(TAG, "Error uploading the file, resuming...");
                    handleTimeoutWithRetries(request, file);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Something went wrong during resumable upload", e);
            handleTimeoutWithRetries(request, file);
        }

    }

    private static void handleTimeoutWithRetries(Request request, File file) {

        Instant start = Instant.now();  // Get the current time

        Duration oneWeek = Duration.of(7, ChronoUnit.DAYS);

        Instant end = start.plus(oneWeek);

        while (Instant.now().isBefore(end)) {
            try {
                request = handle401Status(request);
                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    Log.i(TAG, "Upload successful");
                    return; // Exits loop if successful
                } else {
                    Log.e(TAG, "Retry failed");
                    Thread.sleep(600000);//600000
                    if (response.code() == 401){
                        request = handle401Status(request);
                    }
                }
            } catch (InterruptedException | IOException e) {
                Log.e(TAG, "Retry failed - error catched: ", e);
            }
        }
        Log.e(TAG, "Maximum retry attempts reached, upload failed");
    }

    //Action that handles a 401 response (Authentication failed, token no longer available)
    private static Request handle401Status(Request request) {
        String token = getAccessToken();
        String accessBlobToken = processToken(token);
        Request.Builder requestBuilder = request.newBuilder();
        requestBuilder.header("Authorization", "Bearer " + accessBlobToken);
        request = requestBuilder.build();
        return request;
    }

    //Actions that handles a 308 response
    private static void handle308Status(Response response, File file) {
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.MINUTES)
                .build();
        try {

            // Retrieves the range header from the response
            String rangeHeader = response.header("Range");
            if (rangeHeader != null) {

                // Extracts the end byte of  range to start from next byte
                String[] rangeValues = rangeHeader.replace("bytes=", "").split("-");
                long newStartByte = Long.parseLong(rangeValues[1]) + 1; // Start from the next byte

                // Creates request body for the remaining chunk
                RequestBody chunkBody = createChunkRequestBody(file, newStartByte, CHUNK_SIZE);

                // Builds new request with headers for resuming upload
                Request newRequest = buildRequestWithHeaders(processToken(getAccessToken()), file, newStartByte, file.length() - 1, chunkBody);

                Response newResponse = client.newCall(newRequest).execute();
                if (newResponse.isSuccessful()) {
                    Log.i(TAG, "Upload successful after 308 status");
                } else {
                    Log.e(TAG, "Error uploading chunk after 308 status");
                    handleTimeoutWithRetries(newRequest, file);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling 308 status", e);
        }
    }

    //Action that builds a new request with headers
    private static Request buildRequestWithHeaders(String bearerToken, File file, long startByte, long endByte, RequestBody chunkBody) {
        return new Request.Builder()
                .url("https://stdaflogs.blob.core.windows.net/logcat/" + file.getName())
                .addHeader("Authorization", "Bearer " + bearerToken)
                .addHeader("x-ms-version", "2020-12-06")
                .addHeader("x-ms-date", getXMsDate())
                .addHeader("x-ms-blob-type", "BlockBlob")
                .addHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + file.length())
                .put(chunkBody)
                .build();
    }

    //Action that creates a chunk request body
    private static RequestBody createChunkRequestBody(File file, long startByte, long chunkSize) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/zip");
            }

            @Override
            public long contentLength() {
                return chunkSize;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream inputStream = new FileInputStream(file)) {
                    inputStream.skip(startByte);
                    byte[] buffer = new byte[4096];
                    long bytesRemaining = chunkSize;
                    while (bytesRemaining > 0) {
                        int bytesRead = inputStream.read(buffer);
                        if (bytesRead == -1) {
                            break;
                        }
                        sink.write(buffer, 0, (int) Math.min(bytesRead, bytesRemaining));
                        bytesRemaining -= bytesRead;
                    }
                }
            }
        };
    }

    //Action that retrieves the OAuth2.0 access token
    private static String getAccessToken(){
        String url = "https://login.microsoftonline.com/" + tennantId + "/oauth2/V2.0/token";

        clientId = "92c08464-e708-4d91-b5e4-aa97f66cf92c";
        clientSecret = "b9N8Q~kFBnsPv2vsJmjVjyLvaQbeJJBqUZYaXbgV";
        scope = "https://stdaflogs.blob.core.windows.net/.default";

        String encodedCredentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        // HTTP Connection
        HttpURLConnection connection = connectToHTTP(url, encodedCredentials);

        String parameters = "grant_type=" + grantType + "&scope=" + scope;

        sendRequest(connection, parameters);
        reader = readResponse(connection);
        StringBuilder response = processResponse();
        closeReader();

        return response.toString();
    }

    //Processes the token response
    private static String processToken(String accessToken){
        Gson gson = new Gson();
        TokenResponse response = gson.fromJson(accessToken, TokenResponse.class);
        String accessBlobToken = response.getAccessToken();
        return accessBlobToken;
    }

    //Creates HTTP connection
    private static HttpURLConnection connectToHTTP(String url, String encodedCredentials){
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
        } catch (IOException e) {
            Log.e(TAG, "Error creating HTTP connection", e);
        }
        try {
            connection.setRequestMethod("POST");
        } catch (ProtocolException e) {
            Log.e(TAG, "Error creating request method of HTTP connection", e);
        }
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
        connection.setDoOutput(true);
        return connection;
    }

    //Sends the request
    private static void sendRequest(HttpURLConnection connection, String parameters) {
        try {
            if (connection != null) {
                try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                    outputStream.writeBytes(parameters);
                    outputStream.flush();
                }
            } else {
                Log.e(TAG, "Connection is null");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error sending the request of HTTP connection", e);
        }
    }

    private static BufferedReader readResponse(HttpURLConnection connection){
        int responseCode = 0;
        try {
            responseCode = connection.getResponseCode();
        } catch (IOException e) {
            Log.e(TAG, "Error getting response of HTTP connection", e);
        }
        BufferedReader reader = null;
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } catch (IOException e) {
                Log.e(TAG, "Error initializing HTTP connection response reader", e);
            }
        } else {
            reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        return reader;
    }

    private static StringBuilder processResponse(){
        StringBuilder response = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read response from reader", e);
        }
        return response;
    }

    private static void closeReader(){
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close HTTP connection reader", e);
            }
            reader = null;
        }
    }

    private static String getXMsDate(){
        LocalDateTime localDateTime = LocalDateTime.now();
        ZoneId zoneId = ZoneId.of("GMT");
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, zoneId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");
        String xmsdate = zonedDateTime.format(formatter);

        String regexPattern = "Sept";

        Pattern pattern = Pattern.compile(regexPattern);

        Matcher matcher = pattern.matcher(xmsdate);

        if(matcher.find()){
            xmsdate = xmsdate.replace("Sept", "Sep");
        }

        return xmsdate;
    }

}
