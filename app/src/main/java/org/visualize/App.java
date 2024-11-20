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
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class App implements PitchDetectionHandler {

    private AudioDispatcher dispatcher;
    private PitchEstimationAlgorithm algo;
    private double pitch;
    private float sampleRate = 44100;
    private int bufferSize = 1024;
    private int overlap = 512;
    private String fileName;
    private int numBins = 128;
    private boolean logarithmic = true;
    private int minFrequency = 50;
    private int maxFrequency = 11000;

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
            int[] bins = binFrequencies(amplitudes, fft);

            // calculate amplitudes
            for (int i = 0; i < amplitudes.length; i++) {
                float real = transformBuffer[2 * i];
                float imaginary = transformBuffer[2 * i + 1];
                amplitudes[i] = (float) Math.sqrt(real * real + imaginary * imaginary);
            }

            // normalize magnitude
            float maxAmplitude = 0;
            for (float amplitude : amplitudes) {
                maxAmplitude = Math.max(maxAmplitude, amplitude);
            }

            // amplitude range is from 0 to 1
            for (int i = 0; i < amplitudes.length; i++) {
                amplitudes[i] /= maxAmplitude; 
                //System.out.printf("amp: %d.2", i );
            }

            return true;
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
    }



}
