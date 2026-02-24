package org.example.demo;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class GalleryController {
    private static final double THUMBNAIL_WIDTH = 160;
    private static final double THUMBNAIL_HEIGHT = 120;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    @FXML
    private TilePane thumbnailsPane;

    @FXML
    private ComboBox<String> sortComboBox;

    @FXML
    private TextField renameField;

    @FXML
    private Label statusLabel;

    private final Path galleryDir = Path.of(System.getProperty("user.home"), "JavaGalleryImages");
    private Path selectedImage;

    @FXML
    public void initialize() {
        sortComboBox.setItems(FXCollections.observableArrayList(
                "Имя (A→Я)",
                "Размер (меньше→больше)",
                "Дата (новые→старые)"
        ));
        sortComboBox.getSelectionModel().selectFirst();
        sortComboBox.setOnAction(event -> refreshGallery());

        try {
            Files.createDirectories(galleryDir);
            refreshGallery();
        } catch (IOException e) {
            showError("Ошибка инициализации галереи", e.getMessage());
        }
    }

    @FXML
    private void onUploadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите изображение");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"
        ));

        Stage stage = getCurrentStage();
        if (stage == null) {
            return;
        }

        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        Path source = file.toPath();
        String fileName = source.getFileName().toString();
        Path destination = resolveUniqueName(fileName);

        try {
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
            statusLabel.setText("Изображение загружено: " + destination.getFileName());
            refreshGallery();
        } catch (IOException e) {
            showError("Ошибка загрузки", e.getMessage());
        }
    }

    @FXML
    private void onRenameSelected() {
        if (selectedImage == null) {
            statusLabel.setText("Сначала выберите изображение для переименования.");
            return;
        }

        String newNameInput = renameField.getText() == null ? "" : renameField.getText().trim();
        if (newNameInput.isBlank()) {
            statusLabel.setText("Введите новое имя.");
            return;
        }

        String extension = getFileExtension(selectedImage.getFileName().toString());
        String newFileName = newNameInput + extension;
        Path target = galleryDir.resolve(newFileName);

        if (Files.exists(target)) {
            statusLabel.setText("Файл с таким именем уже существует.");
            return;
        }

        try {
            Files.move(selectedImage, target);
            selectedImage = target;
            renameField.clear();
            statusLabel.setText("Переименовано в: " + newFileName);
            refreshGallery();
        } catch (IOException e) {
            showError("Ошибка переименования", e.getMessage());
        }
    }

    private void refreshGallery() {
        thumbnailsPane.getChildren().clear();
        List<Path> images = loadImages();

        if (images.isEmpty()) {
            statusLabel.setText("Галерея пуста. Загрузите изображение.");
            return;
        }

        for (Path imagePath : images) {
            VBox thumbCard = createThumbnailCard(imagePath);
            thumbnailsPane.getChildren().add(thumbCard);
        }

        if (selectedImage != null && images.contains(selectedImage)) {
            statusLabel.setText("Выбрано: " + selectedImage.getFileName());
        }
    }

    private List<Path> loadImages() {
        List<Path> images = new ArrayList<>();
        try (Stream<Path> stream = Files.list(galleryDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isImage)
                    .forEach(images::add);
        } catch (IOException e) {
            showError("Ошибка чтения галереи", e.getMessage());
        }

        images.sort(currentComparator());
        return images;
    }

    private Comparator<Path> currentComparator() {
        String selectedSort = sortComboBox.getValue();
        if ("Размер (меньше→больше)".equals(selectedSort)) {
            return Comparator.comparingLong(this::safeSize).thenComparing(path -> path.getFileName().toString().toLowerCase());
        }
        if ("Дата (новые→старые)".equals(selectedSort)) {
            return Comparator.comparing(this::safeModifiedTime).reversed()
                    .thenComparing(path -> path.getFileName().toString().toLowerCase());
        }

        return Comparator.comparing(path -> path.getFileName().toString().toLowerCase());
    }

    private VBox createThumbnailCard(Path imagePath) {
        Image preview = new Image(imagePath.toUri().toString(), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true, true);
        ImageView imageView = new ImageView(preview);
        imageView.setFitWidth(THUMBNAIL_WIDTH);
        imageView.setFitHeight(THUMBNAIL_HEIGHT);
        imageView.setPreserveRatio(true);

        Label nameLabel = new Label(imagePath.getFileName().toString());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(THUMBNAIL_WIDTH);

        long size = safeSize(imagePath);
        Label metaLabel = new Label(String.format("%d KB • %s", Math.max(1, size / 1024), DATE_FORMATTER.format(safeModifiedTime(imagePath).toInstant())));

        VBox card = new VBox(6, imageView, nameLabel, metaLabel);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-padding: 8; -fx-border-color: #c9c9c9; -fx-border-radius: 6; -fx-background-radius: 6;");

        card.setOnMouseClicked(event -> {
            selectedImage = imagePath;
            statusLabel.setText("Выбрано: " + imagePath.getFileName());
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openFullSizeImage(imagePath);
            }
        });

        return card;
    }

    private void openFullSizeImage(Path imagePath) {
        Image image = new Image(imagePath.toUri().toString());
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(1000);
        imageView.setFitHeight(700);

        VBox root = new VBox(imageView);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 12;");

        Stage previewStage = new Stage();
        previewStage.setTitle(imagePath.getFileName().toString());
        previewStage.setScene(new Scene(root, 1024, 720));
        previewStage.show();
    }

    private boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    private String getFileExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        return dotIndex == -1 ? "" : name.substring(dotIndex);
    }

    private Path resolveUniqueName(String fileName) {
        Path destination = galleryDir.resolve(fileName);
        if (!Files.exists(destination)) {
            return destination;
        }

        String extension = getFileExtension(fileName);
        String baseName = extension.isEmpty() ? fileName : fileName.substring(0, fileName.length() - extension.length());
        int index = 1;

        while (Files.exists(destination)) {
            destination = galleryDir.resolve(baseName + "_" + index + extension);
            index++;
        }

        return destination;
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private FileTime safeModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.from(Instant.EPOCH);
        }
    }

    private Stage getCurrentStage() {
        if (thumbnailsPane != null && thumbnailsPane.getScene() != null) {
            return (Stage) thumbnailsPane.getScene().getWindow();
        }
        return null;
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}