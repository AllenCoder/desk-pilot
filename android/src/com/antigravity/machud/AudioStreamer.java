package com.antigravity.machud;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class AudioStreamer {
    private static final String TAG = "AudioStreamer";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_BYTES = 2048;

    public interface AmplitudeListener {
        void onAmplitude(float normalizedAmp);
    }

    private AudioRecord audioRecord;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private String targetHost = "127.0.0.1";
    private int targetPort = 9528;
    private AmplitudeListener amplitudeListener;

    public AudioStreamer(String host, int port) {
        if (host != null && !host.isEmpty()) {
            this.targetHost = host;
        }
        this.targetPort = port;
    }

    public void setTargetHost(String host) {
        if (host != null && !host.isEmpty()) {
            this.targetHost = host;
        }
    }

    public void setAmplitudeListener(AmplitudeListener listener) {
        this.amplitudeListener = listener;
    }

    public synchronized boolean start() {
        if (isRecording) return true;

        try {
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int bufferSize = Math.max(minBufferSize, BUFFER_SIZE_BYTES * 2);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return false;
            }

            audioRecord.startRecording();
            isRecording = true;

            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    streamAudio();
                }
            }, "AudioStreamer-Thread");
            recordingThread.setPriority(Thread.MAX_PRIORITY);
            recordingThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio recording", e);
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        isRecording = false;
        if (recordingThread != null) {
            try {
                recordingThread.join(500);
            } catch (InterruptedException ignored) {}
            recordingThread = null;
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    private void streamAudio() {
        Socket tcpSocket = null;
        OutputStream os = null;
        DatagramSocket udpSocket = null;
        byte[] buffer = new byte[BUFFER_SIZE_BYTES];

        try {
            try {
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(targetHost, targetPort), 1000);
                tcpSocket.setTcpNoDelay(true);
                os = tcpSocket.getOutputStream();
            } catch (Exception e) {
                udpSocket = new DatagramSocket();
            }

            InetAddress address = InetAddress.getByName(targetHost);

            while (isRecording && audioRecord != null) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    // 计算瞬时 RMS 音量
                    long sum = 0;
                    for (int i = 0; i < read - 1; i += 2) {
                        short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
                        sum += (long) sample * sample;
                    }
                    double rms = Math.sqrt((double) sum / (read / 2.0));
                    final float amp = (float) Math.min(1.0, rms / 4000.0);
                    if (amplitudeListener != null) {
                        amplitudeListener.onAmplitude(amp);
                    }

                    if (os != null) {
                        os.write(buffer, 0, read);
                    } else if (udpSocket != null) {
                        DatagramPacket packet = new DatagramPacket(buffer, read, address, targetPort);
                        udpSocket.send(packet);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in streamAudio loop", e);
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception ignored) {}
            }
            if (tcpSocket != null) {
                try { tcpSocket.close(); } catch (Exception ignored) {}
            }
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
        }
    }
}
