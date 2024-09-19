package com.speedio.speedio_v1;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SpeedMonitorApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        LoggingConfig.configureLogging(); // Configure logging

        Parent root = FXMLLoader.load(getClass().getResource("/speed_monitor.fxml"));
        primaryStage.setTitle("Internet Speed Monitor");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
