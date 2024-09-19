package com.speedio.speedio_v1;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UploadSpeedCheckHandler {

    private static final Logger logger = Logger.getLogger(UploadSpeedCheckHandler.class.getName());
    private static final String LIBRESPEED_URL = "https://speedio-server-16655e5b4064.herokuapp.com/upload"; // Replace with your Heroku app URL
    private static final int FILE_SIZE_MB = 10; // File size for upload tests
    private static final int TIMEOUT_MS = 300000; // Timeout for HTTP requests (5 minutes)
    private static final int CHUNK_SIZE_MB = 1; // Chunk size (1 MB)


    private final Consumer<Double> uploadSpeedCallback;
    private final int numberOfConnections; // Now fixed in code
    private final String alertEmail;
    private boolean emailSent = false;

    public UploadSpeedCheckHandler(Consumer<Double> uploadSpeedCallback, String alertEmail) {
        this.uploadSpeedCallback = uploadSpeedCallback;
        this.numberOfConnections = 5; // Fixed value
        this.alertEmail = alertEmail;
    }

    public void startSpeedTest() {
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfConnections);
        List<Future<Double>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfConnections; i++) {
            futures.add(executorService.submit(this::checkUploadSpeed));
        }

        executorService.shutdown();

        double totalSpeed = 0.0;
        try {
            for (Future<Double> future : futures) {
                totalSpeed += future.get();
            }
            double averageSpeed = totalSpeed / numberOfConnections;
            uploadSpeedCallback.accept(averageSpeed);

            // Send email alert after speed test is completed
            if (alertEmail != null && !emailSent) {
                sendAlertEmail(averageSpeed);
                emailSent = true;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Error during concurrent upload speed test", e);
        }
    }

    public double checkUploadSpeed() {
        logger.info("Checking upload speed...");

        String fileId = UUID.randomUUID().toString(); // Generate unique file ID for each upload session

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            long startTime = System.nanoTime();

            byte[] data = new byte[CHUNK_SIZE_MB * 1024 * 1024]; // Chunk size (1 MB)
            ByteArrayInputStream dataStream = new ByteArrayInputStream(new byte[FILE_SIZE_MB * 1024 * 1024]);

            long totalChunks = (long) Math.ceil((double) FILE_SIZE_MB / CHUNK_SIZE_MB);

            for (int chunkNumber = 1; chunkNumber <= totalChunks; chunkNumber++) {
                int bytesRead = dataStream.read(data);

                if (bytesRead > 0) {
                    HttpPost uploadFile = new HttpPost(LIBRESPEED_URL);
                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

                    // Add the file data to the request
                    builder.addBinaryBody("file", new ByteArrayInputStream(data, 0, bytesRead),
                            ContentType.APPLICATION_OCTET_STREAM, "testfile_chunk_" + chunkNumber + ".bin");

                    // Add additional fields
                    builder.addTextBody("chunkNumber", String.valueOf(chunkNumber));
                    builder.addTextBody("totalChunks", String.valueOf(totalChunks));
                    builder.addTextBody("fileSizeMB", String.valueOf(FILE_SIZE_MB));
                    builder.addTextBody("fileId", fileId); // Add the unique fileId

                    HttpEntity multipart = builder.build();
                    uploadFile.setEntity(multipart);

                    RequestConfig requestConfig = RequestConfig.custom()
                            .setSocketTimeout(TIMEOUT_MS)
                            .setConnectTimeout(TIMEOUT_MS)
                            .build();
                    uploadFile.setConfig(requestConfig);

                    HttpResponse response = client.execute(uploadFile);
                    String responseBody = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
                            .lines().collect(Collectors.joining("\n"));

                    logger.info("Response for chunk " + chunkNumber + ": " + responseBody);

                    if (!responseBody.contains("Chunk received, waiting for more chunks")) {
                        logger.warning("Unexpected response for chunk " + chunkNumber + ": " + responseBody);
                        return 0.0;
                    }
                } else {
                    logger.warning("Failed to read data for chunk " + chunkNumber);
                    return 0.0;
                }
            }

            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double uploadSpeed = (FILE_SIZE_MB * 8.0) / durationSeconds; // Mbps (Megabits per second)

            // Round to two decimal places
            uploadSpeed = Math.round(uploadSpeed * 100.0) / 100.0;

            logger.info("Upload speed: " + uploadSpeed + " Mbps");
            uploadSpeedCallback.accept(uploadSpeed);

            return uploadSpeed;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error during HTTP upload", e);
            return 0.0;
        }
    }

    private void sendAlertEmail(double uploadSpeed) {
        if (alertEmail != null && !alertEmail.isEmpty()) {
            EmailAlertSender.sendEmailAlert(alertEmail, "Upload Speed Alert", "The upload speed has fallen below the threshold. Current speed: " + uploadSpeed + " Mbps.");
            logger.info("Alert email sent to " + alertEmail);
        } else {
            logger.warning("Email address is empty. Cannot send alert email.");
        }
    }
}
