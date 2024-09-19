package com.speedio.speedio_v1;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.model.SpeedTestError;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadSpeedCheckHandler {

    private static final Logger logger = Logger.getLogger(DownloadSpeedCheckHandler.class.getName());

    private static final String TEST_SERVER_URL = "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_10mb.mp4";
    private static final String WARMUP_URL = "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4";

    private final Consumer<String> downloadSpeedCallback;
    private final Runnable onSpeedBelowThreshold; // Runnable to trigger the email alert
    private final double speedThreshold;
    private double lastDownloadSpeed;
    private final AtomicBoolean emailSentFlag = new AtomicBoolean(false); // Flag to prevent duplicate emails

    public DownloadSpeedCheckHandler(Consumer<String> downloadSpeedCallback, double speedThreshold, Runnable onSpeedBelowThreshold) {
        this.downloadSpeedCallback = downloadSpeedCallback;
        this.speedThreshold = speedThreshold;
        this.onSpeedBelowThreshold = onSpeedBelowThreshold;
        this.lastDownloadSpeed = 0.0;
    }

    public void startSpeedTest() {
        logger.info("Starting speed test...");
        new Thread(() -> {
            if (emailSentFlag.compareAndSet(false, true)) { // Ensure only one thread can send the email
                checkAndUpdateSpeed();

                // After the speed check is completed, decide if the email should be sent
                if (isSpeedBelowThreshold()) {
                    logger.warning("Speed is below the threshold. Sending alert email...");
                    onSpeedBelowThreshold.run(); // Trigger the email alert
                } else {
                    logger.info("Speed is above the threshold. No email will be sent.");
                }
            }
        }).start();
    }

    public void checkAndUpdateSpeed() {
        logger.info("Running speed check...");
        lastDownloadSpeed = checkDownloadSpeed();
        String formattedSpeed = String.format("%.2f Mbps", lastDownloadSpeed);
        downloadSpeedCallback.accept(formattedSpeed);

        // Log both the threshold and the last speed for debugging
        logger.info("Threshold: " + speedThreshold + " Mbps, Last download speed: " + lastDownloadSpeed + " Mbps");
    }

    public double checkDownloadSpeed() {
        logger.fine("Performing warm-up...");
        performWarmUp();

        final double[] speedResult = {0.0};
        final CountDownLatch latch = new CountDownLatch(1);

        SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        speedTestSocket.setSocketTimeout(20000);

        speedTestSocket.addSpeedTestListener(new fr.bmartel.speedtest.inter.ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                double speed = report.getTransferRateBit().doubleValue() / (1024 * 1024); // Convert to Mbps
                speedResult[0] = speed;
                logger.fine("Download speed: " + speed + " Mbps");
                latch.countDown();
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                double currentSpeed = report.getTransferRateBit().doubleValue() / (1024 * 1024); // Convert to Mbps
                String formattedSpeed = String.format("%.2f Mbps", currentSpeed);
                logger.fine("Progress: " + percent + "%, Current speed: " + formattedSpeed);

                // Update the UI with the current speed in real-time
                downloadSpeedCallback.accept(formattedSpeed);
            }

            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                logger.log(Level.SEVERE, "Error during speed test: " + errorMessage);
                latch.countDown();
            }
        });

        speedTestSocket.startDownload(TEST_SERVER_URL);

        try {
            latch.await(); // Wait for the speed test to complete
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Speed test interrupted", e);
        } finally {
            speedTestSocket.closeSocket();
        }

        return speedResult[0];
    }

    private void performWarmUp() {
        SpeedTestSocket warmUpSocket = new SpeedTestSocket();
        warmUpSocket.setSocketTimeout(10000);

        final CountDownLatch warmUpLatch = new CountDownLatch(1);

        warmUpSocket.addSpeedTestListener(new fr.bmartel.speedtest.inter.ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                warmUpLatch.countDown();
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                logger.fine("Warm-up progress: " + percent + "%");
            }

            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                logger.log(Level.SEVERE, "Error during warm-up: " + errorMessage);
                warmUpLatch.countDown();
            }
        });

        warmUpSocket.startDownload(WARMUP_URL);

        try {
            warmUpLatch.await(); // Wait for warm-up to complete
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Warm-up interrupted", e);
        } finally {
            warmUpSocket.closeSocket();
        }
    }

    public boolean isSpeedBelowThreshold() {
        return lastDownloadSpeed < speedThreshold;
    }
}