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
    // private boolean logarithmic = true;

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
            binAmplitudes(amplitudes, transformBuffer);

            for (int i = 0; i < amplitudeBins.length; i++) {
                float real = transformBuffer[2 * i];
                float imaginary = transformBuffer[2 * i + 1];
                amplitudeBins[i] = (float) Math.sqrt(real * real + imaginary * imaginary);
            }

            double maxAmplitude = 0;
            for (double amplitude : amplitudeBins) {
                maxAmplitude = Math.max(maxAmplitude, amplitude);
            }

            for (int i = 0; i < amplitudeBins.length; i++) {
                amplitudeBins[i] /= maxAmplitude;
            }

            currentTime = audioEvent.getTimeStamp();
            updateBars();
            return true;
        }

        private void binAmplitudes(float[] amplitudes2, float[] transformBuffer) {
            for (int i = 0; i < numBins; i++) {
                double startFreq = minFrequency * Math.pow(maxFrequency / minFrequency, (double) i / numBins);
                double endFreq = minFrequency * Math.pow(maxFrequency / minFrequency, (double) (i + 1) / numBins);

                int startIndex = (int) Math.floor(startFreq * bufferSize / sampleRate);
                int endIndex = (int) Math.floor(endFreq * bufferSize / sampleRate);

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
        List<String> results = new ArrayList<String>();

        File[] files = new File("src/main/resources").listFiles();

        for (File file : files) {
            if (file.isFile()) {
                results.add(file.getName());
            }
        }

        ListView<String> listView = new ListView<>();
        listView.getItems().addAll(results);
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
        menu.getChildren().addAll(listView, playButton, pauseButton);

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

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.BLACK);
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

    /*
     * public int[] binFrequencies(float[] amplitudes, FFT fft) {
     * List<Double> frequencies = extractFrequencies(amplitudes, fft);
     * int[] bins = new int[numBins];
     * for (double frequency : frequencies) {
     * int binIndex = frequencyToBin(frequency);
     * if (binIndex >= 0 && binIndex < numBins) {
     * bins[binIndex]++;
     * }
     * }
     * return bins;
     * }
     */

    /*
     * private List<Double> extractFrequencies(float[] amplitudes, FFT fft) {
     * List<Double> frequencies = new ArrayList<>();
     * 
     * for (int binIndex = 0; binIndex < amplitudes.length; binIndex++) {
     * double frequency = binIndex * sampleRate / bufferSize;
     * 
     * if (amplitudes[binIndex] > 0.01) {
     * frequencies.add(frequency);
     * }
     * }
     * return frequencies;
     * }
     */

    /*
     * private int frequencyToBin(double frequency) {
     * if (frequency < minFrequency || frequency > maxFrequency)
     * return -1;
     * 
     * if (logarithmic) {
     * double logMin = Math.log10(minFrequency);
     * double logMax = Math.log10(maxFrequency);
     * double logFreq = Math.log10(frequency);
     * return (int) ((logFreq - logMin) / (logMax - logMin) * numBins);
     * } else {
     * return (int) ((frequency - minFrequency) / (maxFrequency - minFrequency) *
     * numBins);
     * }
     * }
     */

    /*
     * public double[] getBinRange(int binIndex) {
     * if (logarithmic) {
     * double logMin = Math.log10(minFrequency);
     * double logMax = Math.log10(maxFrequency);
     * double logBinWidth = (logMax - logMin) / numBins;
     * double lowerBound = Math.pow(10, logMin + binIndex * logBinWidth);
     * double upperBound = Math.pow(10, logMin + (binIndex + 1) * logBinWidth);
     * return new double[] { lowerBound, upperBound };
     * } else {
     * double binWidth = (maxFrequency - minFrequency) / numBins;
     * double lowerBound = minFrequency + binIndex * binWidth;
     * double upperBound = minFrequency + (binIndex + 1) * binWidth;
     * return new double[] { lowerBound, upperBound };
     * }
     * }
     */
}
