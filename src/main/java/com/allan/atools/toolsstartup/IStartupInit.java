package com.allan.atools.toolsstartup;

import javafx.stage.Stage;

public interface IStartupInit {
    void beforeStart(Stage stage);
    void createMainView(Stage stage);
    String[] getCssPaths();
}
