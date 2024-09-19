module com.speedio.speedio_v1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires jakarta.mail;
    requires org.apache.commons.net;
    requires jspeedtest;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires org.apache.httpcomponents.httpcore;  // Use 'static' for non-modularized libraries
    requires org.apache.httpcomponents.httpclient;
    requires org.apache.httpcomponents.httpmime;
    requires javafx.graphics;



    opens com.speedio.speedio_v1 to javafx.fxml;
    exports com.speedio.speedio_v1;
}