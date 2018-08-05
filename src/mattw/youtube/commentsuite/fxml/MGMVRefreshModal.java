package mattw.youtube.commentsuite.fxml;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mattw.youtube.commentsuite.db.Group;
import mattw.youtube.commentsuite.io.ClipboardUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Modal for group refreshing set as content within an OverlayModal.
 *
 * @author mattwright324
 */
public class MGMVRefreshModal extends HBox {

    private static Logger logger = LogManager.getLogger(MGMVRefreshModal.class.getSimpleName());

    private static Image angleLeft = new Image("/mattw/youtube/commentsuite/img/angle-left.png");
    private static Image angleRight = new Image("/mattw/youtube/commentsuite/img/angle-right.png");
    private static Image circleCheck = new Image("/mattw/youtube/commentsuite/img/check-circle.png");
    private static Image circleTimes = new Image("/mattw/youtube/commentsuite/img/times-circle.png");
    private static Image circleMinus = new Image("/mattw/youtube/commentsuite/img/minus-circle.png");

    private ClipboardUtil clipboard = new ClipboardUtil();

    class RefreshExample extends Thread {
        private Logger logger = LogManager.getLogger(getClass().getSimpleName());

        private Group group;
        private double max = 1000*20.0;
        private boolean stoppedOnError = false;
        private SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");

        SimpleBooleanProperty finished = new SimpleBooleanProperty(false);
        SimpleDoubleProperty progress = new SimpleDoubleProperty(0.0);
        ObservableList<String> errorList = FXCollections.observableArrayList();

        public RefreshExample(Group group) {
            this.group = group;
        }

        public void run() {
            logger.debug("Refresh Start");
            try {
                long start = System.currentTimeMillis();
                double diff;
                while((diff = System.currentTimeMillis() - start) <= max && !Thread.currentThread().isInterrupted()) {
                    try {
                        double p = diff / max;
                        if(diff > max/2) {
                            throw new Exception("Test failure.");
                        }
                        Platform.runLater(() -> progress.setValue(p));
                        try { Thread.sleep(100); } catch (Exception ignored) {}
                    } catch (InterruptedException e) {
                        logger.debug("Thread Interrupted", e);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                appendError(e.getLocalizedMessage());
                logger.error("Refresh Failed", e);
                stoppedOnError = true;
            }
            if(this.isInterrupted()) {
                logger.debug("Refresh Interrupted");
            }
            Platform.runLater(() -> finished.setValue(true));
            logger.debug("Refresh Finished");
        }

        public void appendError(String msg) {
            String message = sdf.format(new Date()) + " - " + msg;
            Platform.runLater(() -> errorList.add(message));
        }

        public SimpleBooleanProperty finishedProperty() {
            return finished;
        }

        public SimpleDoubleProperty progressProperty() {
            return progress;
        }

        public boolean isStoppedOnError() {
            return stoppedOnError;
        }
    }

    @FXML Label alert;
    @FXML Button btnClose;
    @FXML Button btnStart;
    @FXML ProgressBar progressBar;
    @FXML VBox statusPane;

    @FXML ImageView expandIcon;
    @FXML ListView<String> errorList;
    @FXML Hyperlink expand;
    @FXML ImageView endStatus;
    @FXML ProgressIndicator statusIndicator;

    private Group group;
    private RefreshExample example;
    private boolean running = false;

    private boolean expanded = false;

    public MGMVRefreshModal(Group group) {
        this.group = group;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("MGMVRefreshModal.fxml"));
        loader.setController(this);
        loader.setRoot(this);
        try {
            loader.load();

            expandIcon.setImage(angleRight);

            expand.setOnAction(ae -> {
                expanded = !expanded;
                Platform.runLater(() -> {
                    if(expanded) {
                        expandIcon.setImage(angleLeft);
                        errorList.setManaged(true);
                        errorList.setVisible(true);
                    } else {
                        expandIcon.setImage(angleRight);
                        errorList.setManaged(false);
                        errorList.setVisible(false);
                    }
                });
            });

            errorList.setOnKeyPressed(ke -> {
                if(ke.getCode() == KeyCode.C && ke.isControlDown()) {
                    clipboard.setClipboard(errorList.getItems());
                }
            });

            btnStart.setOnAction(ae -> new Thread(() -> {
                if(running) {
                    logger.debug(String.format("Requesting group refresh stopped for group", group.getId(), group.getName()));
                    Platform.runLater(() -> {
                        btnStart.setDisable(true);
                        endStatus.setImage(circleMinus);
                    });
                    example.interrupt();
                    while(example.isAlive()) {
                        try { Thread.sleep(250); } catch (Exception ignored) {}
                    }
                    running = false;
                    Platform.runLater(() -> {
                        btnClose.setDisable(false);
                    });
                } else {
                    running = true;
                    logger.debug(String.format("Starting group refresh for group [id=%s,name=%s]", group.getId(), group.getName()));
                    Platform.runLater(() -> {
                        statusPane.setVisible(true);
                        statusPane.setManaged(true);
                        btnClose.setDisable(true);
                        btnStart.setText("Stop");
                        alert.setVisible(false);
                        alert.setManaged(false);
                    });

                    example = new RefreshExample(group);
                    errorList.setItems(example.errorList);
                    progressBar.progressProperty().bind(example.progressProperty());
                    example.start();
                    example.finishedProperty().addListener((o, ov, nv) -> {
                        progressBar.progressProperty().unbind();
                        Platform.runLater(() -> {
                            btnStart.setVisible(false);
                            btnStart.setManaged(false);
                            btnClose.setDisable(false);
                            endStatus.setImage(example.isStoppedOnError() ? circleTimes : circleCheck);
                            endStatus.setManaged(true);
                            endStatus.setVisible(true);
                            statusIndicator.setManaged(false);
                            statusIndicator.setVisible(false);
                        });
                    });
                }
            }).start());
        } catch (IOException e) { logger.error(e); }
    }

    /**
     * Reset the modal back to its original state when being opened.
     */
    public void reset() {
        logger.debug("Resetting state of Refresh Modal");
        running = false;
        Platform.runLater(() -> {
            if(expanded) {
                expand.fire();
            }
            endStatus.setManaged(false);
            endStatus.setVisible(false);
            statusIndicator.setManaged(true);
            statusIndicator.setVisible(true);
            errorList.getItems().clear();
            alert.getStyleClass().remove("alertSuccess");
            alert.getStyleClass().add("alertWarning");
            alert.setVisible(true);
            alert.setManaged(true);
            statusPane.setVisible(false);
            statusPane.setManaged(false);
            btnStart.setVisible(true);
            btnStart.setManaged(true);
            btnStart.setDisable(false);
            btnStart.setText("Start");
            btnStart.getStyleClass().remove("btnWarning");
            btnStart.getStyleClass().add("btnPrimary");
        });
    }

    public Button getBtnClose() { return btnClose; }

    public Button getBtnStart() { return btnStart; }

    public ListView<String> getErrorList() { return errorList; }
}
