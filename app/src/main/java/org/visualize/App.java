package org.visualize;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import be.tarsos.dsp.io.jvm.AudioPlayer;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import be.tarsos.dsp.util.fft.FFT;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class App extends Application {

    private Thread audioThread;
    private AudioDispatcher dispatcher;
    private PitchEstimationAlgorithm algo = PitchEstimationAlgorithm.YIN;
    GainProcessor gainProcessor = new GainProcessor(1.0);
    private boolean isPlaying;
    private double pauzedAt;
    private double currentTime;
    private float sampleRate = 44100;
    private int bufferSize = 2048;
    private int overlap = 1024;
    private String fileName;
    private int numBins = 128;
    private int minFrequency = 50;
    private int maxFrequency = 11000;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;
    private Rectangle[] bars = new Rectangle[numBins];
    private double[] amplitudeBins = new double[numBins];
    private String currentDirectory;

    private void setDispatch() {
        if (dispatcher != null) {
            dispatcher.stop();
        }
        try {
            File audioFile = new File("src/main/resources/" + fileName);
            dispatcher = AudioDispatcherFactory.fromFile(audioFile, bufferSize, overlap);
            AudioFormat format = AudioSystem.getAudioFileFormat(audioFile).getFormat();
            dispatcher.addAudioProcessor(new AudioPlayer(format));
        } catch (Exception e) {
            e.printStackTrace();
        }

        dispatcher.addAudioProcessor(fftProcessor);
        dispatcher.skip(pauzedAt);

        new Thread(dispatcher, "Audio dispatching").start();
    }

    AudioProcessor fftProcessor = new AudioProcessor() {
        FFT fft = new FFT(bufferSize);
        float[] amplitudes = new float[bufferSize / 2];

        @Override
        public boolean process(AudioEvent audioEvent) {
            float[] audioFloatBuffer = audioEvent.getFloatBuffer();
            float[] transformBuffer = new float[bufferSize * 2];
            System.arraycopy(audioFloatBuffer, 0, transformBuffer, 0, audioFloatBuffer.length);

            fft.forwardTransform(transformBuffer);
            fft.modulus(transformBuffer, amplitudes);
            // Organize the calculated amplitudes into frequency bins
            binAmplitudes(amplitudes, transformBuffer);

            // Calculate and normalize the amplitudes for each bin
            for (int i = 0; i < amplitudeBins.length; i++) {
                float real = transformBuffer[2 * i];
                float imaginary = transformBuffer[2 * i + 1];
                amplitudeBins[i] = (float) Math.sqrt(real * real + imaginary * imaginary);
            }

            // Find the maximum amplitude for normalization
            double maxAmplitude = 0;
            for (double amplitude : amplitudeBins) {
                maxAmplitude = Math.max(maxAmplitude, amplitude);
            }

            // Normalize the amplitude bins to the range [0, 1]
            for (int i = 0; i < amplitudeBins.length; i++) {
                amplitudeBins[i] /= maxAmplitude;
            }

            currentTime = audioEvent.getTimeStamp();
            updateBars();
            return true;
        }

        private void binAmplitudes(float[] amplitudes2, float[] transformBuffer) {
            for (int i = 0; i < numBins; i++) {
                // Define the frequency range for this bin (logarithmic scaling)
                double startFreq = minFrequency * Math.pow(maxFrequency / minFrequency, (double) i / numBins);
                double endFreq = minFrequency * Math.pow(maxFrequency / minFrequency, (double) (i + 1) / numBins);

                // Calculate the corresponding indices in the FFT output
                int startIndex = (int) Math.floor(startFreq * bufferSize / sampleRate);
                int endIndex = (int) Math.floor(endFreq * bufferSize / sampleRate);

                // Sum the amplitudes within this frequency range
                double amplitudeSum = 0;
                for (int j = startIndex; j < endIndex && j < amplitudes.length; j++) {
                    amplitudeSum += amplitudes[j];
                }
                amplitudeBins[i] = amplitudeSum / (endIndex - startIndex + 1);
            }
        }

        @Override
        public void processingFinished() {
        }
    };

    @Override
    public void start(Stage primaryStage) throws Exception {
        ListView<String> listView = new ListView<>();
        List<String> results = new ArrayList<>();

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select a Directory");

        Button chooseDir = new Button("Choose Directory");
        chooseDir.setOnAction(e -> {
            File selectedDirectory = directoryChooser.showDialog(primaryStage);

            if (selectedDirectory != null) {
                currentDirectory = selectedDirectory.getAbsolutePath();
                System.out.println("Selected Directory: " + currentDirectory);

                results.clear();

                File[] files = new File(currentDirectory).listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".wav")) {
                            results.add(file.getName());
                        }
                    }
                }

                listView.getItems().setAll(results);
            }
        });

        listView.setOnMouseClicked((MouseEvent event) -> newAudio(listView));

        Button playButton = new Button("Play");
        Button pauseButton = new Button("Pause");
        playButton.setOnAction(e -> playAudio());
        pauseButton.setOnAction(e -> pauseAudio());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: black;");

        VBox menu = new VBox(10);
        menu.setPadding(new Insets(10));
        menu.setStyle("-fx-background-color: black;");
        menu.getChildren().addAll(listView, playButton, pauseButton, chooseDir);

        Pane visualizerPane = new Pane();
        visualizerPane.setStyle("-fx-background-color: black;");
        double barWidth = (double) WIDTH / numBins;

        for (int i = 0; i < numBins; i++) {
            Rectangle bar = new Rectangle();
            bar.setWidth(barWidth - 2);
            bar.setHeight(0);
            bar.setX(i * barWidth);
            bar.setY(HEIGHT);
            bar.setFill(Color.BLUE);
            bars[i] = bar;
            visualizerPane.getChildren().add(bar);
        }

        root.setLeft(menu);
        root.setCenter(visualizerPane);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        String css = new File("src/main/resources/styles.css").toURI().toURL().toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setTitle("Electric Sheep");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void pauseAudio() {
        if (isPlaying) {
            if (dispatcher != null) {
                dispatcher.stop();
            }
            if (audioThread != null && audioThread.isAlive()) {
                try {
                    audioThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            pauzedAt = currentTime;
            isPlaying = false;
        }
    }

    private void playAudio() {
        if (!isPlaying) {
            if (dispatcher != null) {
                dispatcher.stop();
            }
            if (audioThread != null && audioThread.isAlive()) {
                try {
                    audioThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            isPlaying = true;
            setDispatch();
        }
    }

    private void newAudio(ListView<String> listView) {
        String selectedFile = listView.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            if (dispatcher != null) {
                dispatcher.stop();
            }
            if (audioThread != null && audioThread.isAlive()) {
                try {
                    audioThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            pauzedAt = 0;
            currentTime = 0;
            fileName = selectedFile;
            isPlaying = true;
            setDispatch();
        }
    }

    private void updateBars() {
        javafx.application.Platform.runLater(() -> {
            for (int i = 0; i < amplitudeBins.length; i++) {
                double amplitude = amplitudeBins[i];
                double barHeight = amplitude * HEIGHT;
                bars[i].setHeight(barHeight);
                bars[i].setY(HEIGHT - barHeight);
                bars[i].setFill(Color.color(amplitude, 0.5, 1.0));
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
