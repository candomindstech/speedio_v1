package com.speedio.speedio_v1;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class EmailAlertSender {

    private static final Logger logger = Logger.getLogger(EmailAlertSender.class.getName());

    private static final int MAX_EMAILS_PER_DAY = 3;
    private static Map<LocalDate, Integer> emailCountMap = new HashMap<>();

    public static void sendEmailAlert(String to, String subject, String body) {
        LocalDate today = LocalDate.now();

        // Initialize today's count if not present
        emailCountMap.putIfAbsent(today, 0);

        int emailsSentToday = emailCountMap.get(today);

        if (emailsSentToday < MAX_EMAILS_PER_DAY) {
            // Proceed to send the email
            try {
                URL url = new URL("https://emailio-c9198bebc9fe.herokuapp.com/api/email/send");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String urlParameters = "to=" + to + "&subject=" + subject + "&body=" + body;

                OutputStream os = con.getOutputStream();
                os.write(urlParameters.getBytes());
                os.flush();
                os.close();

                int responseCode = con.getResponseCode();
                System.out.println("POST Response Code :: " + responseCode);

                // Increment the count of emails sent today
                emailCountMap.put(today, emailsSentToday + 1);

                logger.info("Email sent to " + to + " with subject: " + subject);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            logger.warning("Maximum number of emails sent today. No more emails will be sent.");
        }

        // Cleanup for old entries
        cleanupOldEntries();
    }

    private static void cleanupOldEntries() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        emailCountMap.entrySet().removeIf(entry -> entry.getKey().isBefore(yesterday));
    }
}