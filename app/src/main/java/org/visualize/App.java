package org.visualize;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import be.tarsos.dsp.util.fft.FFT;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class App extends Application implements PitchDetectionHandler {

    private AudioDispatcher dispatcher;
    private PitchEstimationAlgorithm algo;
    private double pitch;
    private float sampleRate = 44100;
    private int bufferSize = 2048;
    private int overlap = 1024;
    private String fileName;
    private int numBins = 128;
    private boolean logarithmic = true;
    private int minFrequency = 50;
    private int maxFrequency = 11000;
    private static final int WIDTH = 800; // Window width
    private static final int HEIGHT = 400;
    private Rectangle[] bars = new Rectangle[numBins];
    private double[] amplitudeBins = new double[numBins];

    public App() {
        this.fileName = "24003__erdie__explosion-mega-thunder.wav";
        this.algo = PitchEstimationAlgorithm.YIN;
        setDispatch();
    }

    private void setDispatch() {
        if (dispatcher != null) {
            dispatcher.stop();
        }

        try {
            File audioFile = new File(fileName);
            dispatcher = AudioDispatcherFactory.fromFile(audioFile, bufferSize, overlap);
            AudioFormat format = AudioSystem.getAudioFileFormat(audioFile).getFormat();
        } catch (Exception e) {
            e.printStackTrace();
        }

        dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, this));
        dispatcher.addAudioProcessor(fftProcessor);

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
            // int[] bins = binFrequencies(amplitudes, fft);
            binAmplitudes(amplitudes, transformBuffer);

            // calculate amplitudes
            for (int i = 0; i < amplitudeBins.length; i++) {
                float real = transformBuffer[2 * i];
                float imaginary = transformBuffer[2 * i + 1];
                amplitudeBins[i] = (float) Math.sqrt(real * real + imaginary * imaginary);
            }

            // normalize magnitude
            double maxAmplitude = 0;
            for (double amplitude : amplitudeBins) {
                maxAmplitude = Math.max(maxAmplitude, amplitude);
            }

            // amplitude range is from 0 to 1
            for (int i = 0; i < amplitudeBins.length; i++) {
                amplitudeBins[i] /= maxAmplitude;
                System.out.printf("amp: %f ", amplitudeBins[i]);
            }

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

                amplitudeBins[i] = amplitudeSum / (endIndex - startIndex + 1); // Average amplitude
            }
        }

        @Override
        public void processingFinished() {
            // TODO Auto-generated method stub
        }

    };

    @Override
    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
        if (pitchDetectionResult.isPitched()) {
            pitch = pitchDetectionResult.getPitch();
        } else {
            pitch = -1;
        }
    }

    public int[] binFrequencies(float[] amplitudes, FFT fft) {
        List<Double> frequencies = extractFrequencies(amplitudes, fft);
        int[] bins = new int[numBins];
        for (double frequency : frequencies) {
            int binIndex = frequencyToBin(frequency);
            if (binIndex >= 0 && binIndex < numBins) {
                bins[binIndex]++;
            }
        }
        return bins;
    }

    private List<Double> extractFrequencies(float[] amplitudes, FFT fft) {
        List<Double> frequencies = new ArrayList<>();

        for (int binIndex = 0; binIndex < amplitudes.length; binIndex++) {
            double frequency = binIndex * sampleRate / bufferSize;

            if (amplitudes[binIndex] > 0.01) {
                frequencies.add(frequency);
            }
        }
        return frequencies;
    }

    private int frequencyToBin(double frequency) {
        if (frequency < minFrequency || frequency > maxFrequency)
            return -1;

        if (logarithmic) {
            double logMin = Math.log10(minFrequency);
            double logMax = Math.log10(maxFrequency);
            double logFreq = Math.log10(frequency);
            return (int) ((logFreq - logMin) / (logMax - logMin) * numBins);
        } else {
            return (int) ((frequency - minFrequency) / (maxFrequency - minFrequency) * numBins);
        }
    }

    public double[] getBinRange(int binIndex) {
        if (logarithmic) {
            double logMin = Math.log10(minFrequency);
            double logMax = Math.log10(maxFrequency);
            double logBinWidth = (logMax - logMin) / numBins;
            double lowerBound = Math.pow(10, logMin + binIndex * logBinWidth);
            double upperBound = Math.pow(10, logMin + (binIndex + 1) * logBinWidth);
            return new double[] { lowerBound, upperBound };
        } else {
            double binWidth = (maxFrequency - minFrequency) / numBins;
            double lowerBound = minFrequency + binIndex * binWidth;
            double upperBound = minFrequency + (binIndex + 1) * binWidth;
            return new double[] { lowerBound, upperBound };
        }
    }

    public static void main(String[] args) {

        new Thread(
                new Runnable() {

                    @Override
                    public void run() {
                        new App();
                    }
                }).start();
        // launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Pane root = new Pane();
        double barWidth = (double) WIDTH / numBins;

        for (int i = 0; i < numBins; i++) {
            Rectangle bar = new Rectangle();
            bar.setWidth(barWidth - 2);
            bar.setHeight(0);
            bar.setX(i * barWidth);
            bar.setY(HEIGHT);
            bar.setFill(Color.BLUE);
            bars[i] = bar;
            root.getChildren().add(bar);
        }

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.BLACK);
        primaryStage.setTitle("Electric Sheep");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
