package com.speedio.speedio_v1;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

public class SpeedMonitorController {

    private static final Logger logger = Logger.getLogger(SpeedMonitorController.class.getName());

    @FXML
    private Label downloadSpeedLabel;
    @FXML
    private TextField emailField;
    @FXML
    private TextField thresholdField;
    @FXML
    private Button retryDownloadButton;
    @FXML
    private Button startMonitoringButton;

    private DownloadSpeedCheckHandler downloadSpeedCheckHandler;
    private final AtomicBoolean emailSent = new AtomicBoolean(false);

    @FXML
    private void initialize() {
        startDownloadSpeedCheck();
    }

    private void initializeDownloadSpeedCheckHandler() {
        String thresholdText = thresholdField.getText();
        double threshold = 0.0;

        logger.info("Threshold field raw value: " + thresholdText);

        try {
            threshold = Double.parseDouble(thresholdText);
            logger.info("Parsed threshold: " + threshold);
        } catch (NumberFormatException e) {
            logger.warning("Invalid threshold value. Using default threshold.");
        }

        // Initialize the handler with the complete action only.
        downloadSpeedCheckHandler = new DownloadSpeedCheckHandler(speed -> {
            Platform.runLater(() -> downloadSpeedLabel.setText(speed));
        }, threshold, () -> {
            // After the test is completed, check if speed is below the threshold.
            if (downloadSpeedCheckHandler.isSpeedBelowThreshold() && emailSent.compareAndSet(false, true)) {
                sendAlertEmail(downloadSpeedLabel.getText());  // Pass the current speed to the email
            }
        });
    }

    @FXML
    private void startMonitoring() {
        logger.info("Start Monitoring button clicked");
        startMonitoringButton.setDisable(true); // Disable the button when monitoring starts
        showMonitoringMessage();
    }

    private void showMonitoringMessage() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Speedio Monitoring");
        alert.setHeaderText(null);
        alert.setContentText("The Speedio app will monitor your download speed in the background.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                startDownloadSpeedCheck();
                minimizeWindow();
            }
        });
    }

    private void minimizeWindow() {
        Stage stage = (Stage) emailField.getScene().getWindow();
        Platform.runLater(() -> stage.setIconified(true));
    }

    private void startDownloadSpeedCheck() {
        logger.info("Starting download speed check...");
        emailSent.set(false);

        initializeDownloadSpeedCheckHandler();
        downloadSpeedCheckHandler.startSpeedTest();
    }

    @FXML
    private void retryDownloadSpeed() {
        logger.info("Retrying download speed check...");
        startDownloadSpeedCheck();
    }

    private void sendAlertEmail(String currentSpeed) {
        String email = emailField.getText();
        String threshold = thresholdField.getText();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (email != null && !email.isEmpty()) {
            String subject = "Sysmonitor.io - Internet download Speed Alert";
            String body = String.format(
                    "Dear User,%n%n" +
                            "We have detected that your internet d  ownload speed has fallen below the specified threshold.%n%n" +
                            "Details:%n" +
                            "------------------------------------%n" +
                            "Timestamp: %s%n" +
                            "Threshold: %s Mbps%n" +
                            "Current Speed: %s %n%n" +
                            "This may affect your online experience, including streaming, downloading, and other activities that rely on a stable internet connection.%n%n" +
                            "We recommend checking your network or contacting your internet service provider if the issue persists.%n%n" +
                            "Best regards,%n" +
                            "The Sysmonitor.io Team"
                    , timestamp, threshold, currentSpeed);

            EmailAlertSender.sendEmailAlert(email, subject, body);
            logger.info("Alert email sent to " + email);
        } else {
            logger.warning("Email address is empty. Cannot send alert email.");
        }
    }
}