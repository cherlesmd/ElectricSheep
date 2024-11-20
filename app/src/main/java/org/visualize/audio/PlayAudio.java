package org.visualize.audio;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class PlayAudio {
    private Boolean isPlaying = false;
    private String filePath;
    private Clip clip;

    public PlayAudio(String filePath) {
        this.filePath = filePath;
        try {
            File file = new File(this.filePath);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioStream.getFormat();
            byte[] buffer = new byte[1024];
            int bytesRead = audioStream.read(buffer);
            processAudio(buffer, format);
            // clip = AudioSystem.getClip();
            // clip.open(audioStream);

        } catch (UnsupportedAudioFileException | IOException /* | LineUnavailableException */ e) {
            System.out.println("Error loading audio: " + e.getMessage());
            return;
        }

    }

    private void processAudio(byte[] buffer, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits() / 8;
        for (int i = 0; i < buffer.length; i += sampleSize) {
            int sample = (buffer[i + 1]) << 8 | (buffer[i] & 0XFF);
            System.out.println(sample);
        }

    }

    public void play() {
        if (!isPlaying) {
            clip.start();
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            isPlaying = true;
        }
    }

    public void pause() {
        if (isPlaying) {
            clip.stop();
            isPlaying = false;
        }
    }

    public void restart() {
        clip.setMicrosecondPosition(0);
    }

    public void close() {
        if (clip != null && clip.isOpen()) {
            clip.close();
        }
    }

}
