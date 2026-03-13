package com.example.decibeldetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int EXTRA_BOTTOM_SPACING_DP = 24;

    private static final String LABEL_CURRENT = "%d dB";
    private static final String LABEL_MAX = "%d dB";
    private static final String LABEL_MIN = "%d dB";
    private static final String LABEL_CURRENT_RESET = "0 dB";
    private static final String LABEL_MAX_EMPTY = "-- dB";
    private static final String LABEL_MIN_EMPTY = "-- dB";
    private static final String MESSAGE_PERMISSION_REQUIRED = "需要麦克风权限后才能开始检测";

    private AudioRecord audioRecord;
    private Thread recordingThread;
    private DecibelGaugeView decibelGaugeView;
    private WaveformView waveformView;
    private SwitchCompat detectionSwitch;
    private TextView tvCurrentDb;
    private TextView tvMaxDb;
    private TextView tvMinDb;

    private volatile boolean isRecording = false;
    private volatile int sessionId = 0;

    private double maxDb = Double.MIN_VALUE;
    private double minDb = Double.MAX_VALUE;

    private final String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom + dpToPx(EXTRA_BOTTOM_SPACING_DP)
            );
            return insets;
        });

        initView();
    }

    private void initView() {
        decibelGaugeView = findViewById(R.id.decibel_gauge);
        waveformView = findViewById(R.id.waveform_view);
        detectionSwitch = findViewById(R.id.detection_switch);
        tvCurrentDb = findViewById(R.id.current_db);
        tvMaxDb = findViewById(R.id.max_db);
        tvMinDb = findViewById(R.id.min_db);

        resetStats();
        detectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkPermissions()) {
                    resetCurrentState();
                    startSoundLevelMeasurement();
                } else {
                    requestPermissions();
                }
            } else {
                stopSoundLevelMeasurement();
                resetCurrentState();
            }
        });
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (detectionSwitch != null && detectionSwitch.isChecked()) {
                resetCurrentState();
                startSoundLevelMeasurement();
            }
            return;
        }

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (detectionSwitch != null) {
                detectionSwitch.setChecked(false);
            }
            Toast.makeText(this, MESSAGE_PERMISSION_REQUIRED, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (detectionSwitch != null && detectionSwitch.isChecked() && checkPermissions()) {
            startSoundLevelMeasurement();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSoundLevelMeasurement();
    }

    @Override
    protected void onDestroy() {
        stopSoundLevelMeasurement();
        super.onDestroy();
    }

    private void startSoundLevelMeasurement() {
        if (isRecording) {
            return;
        }

        final int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size: " + bufferSize);
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        AudioRecord newAudioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );
        if (newAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            newAudioRecord.release();
            return;
        }

        audioRecord = newAudioRecord;
        final int currentSessionId = ++sessionId;
        isRecording = true;
        audioRecord.startRecording();
        recordingThread = new Thread(() -> recordLoop(newAudioRecord, bufferSize, currentSessionId), "AudioRecorder");
        recordingThread.start();
    }

    private void recordLoop(AudioRecord record, int bufferSize, int currentSessionId) {
        short[] buffer = new short[bufferSize];
        while (isRecording && currentSessionId == sessionId) {
            int read = record.read(buffer, 0, bufferSize);
            if (read <= 0 || !isRecording || currentSessionId != sessionId) {
                continue;
            }

            final double dbValue = calculateDecibel(buffer, read);
            final short[] waveformBuffer = copyForWaveform(buffer, read);
            runOnUiThread(() -> {
                if (!isDestroyed()
                        && currentSessionId == sessionId
                        && detectionSwitch != null
                        && detectionSwitch.isChecked()) {
                    updateUi(dbValue, waveformBuffer, read);
                }
            });
        }
    }

    private double calculateDecibel(short[] buffer, int length) {
        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }

        double rms = Math.sqrt(sum / length);
        return rms > 0 ? 20.0 * Math.log10(rms) : 0.0;
    }

    private void stopSoundLevelMeasurement() {
        isRecording = false;
        sessionId++;

        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record != null) {
            try {
                record.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "AudioRecord stop failed", e);
            }
            record.release();
        }

        Thread thread = recordingThread;
        recordingThread = null;
        if (thread != null) {
            try {
                thread.join(300);
            } catch (InterruptedException e) {
                Log.e(TAG, "Recording thread join interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private short[] copyForWaveform(short[] buffer, int length) {
        short[] copy = new short[length];
        System.arraycopy(buffer, 0, copy, 0, length);
        return copy;
    }

    private void updateUi(double dbValue, short[] waveformBuffer, int waveformLength) {
        waveformView.setWaveform(waveformBuffer, waveformLength);
        decibelGaugeView.setCurrentDb(dbValue);

        if (dbValue > 0.0) {
            tvCurrentDb.setText(String.format(Locale.getDefault(), LABEL_CURRENT, Math.round(dbValue)));

            if (dbValue > maxDb) {
                maxDb = dbValue;
                tvMaxDb.setText(String.format(Locale.getDefault(), LABEL_MAX, Math.round(maxDb)));
            }
            if (dbValue < minDb) {
                minDb = dbValue;
                tvMinDb.setText(String.format(Locale.getDefault(), LABEL_MIN, Math.round(minDb)));
            }
        } else {
            tvCurrentDb.setText(LABEL_CURRENT_RESET);
            if (maxDb == Double.MIN_VALUE) {
                tvMaxDb.setText(LABEL_MAX_EMPTY);
            }
            if (minDb == Double.MAX_VALUE) {
                tvMinDb.setText(LABEL_MIN_EMPTY);
            }
        }
    }

    private void resetStats() {
        maxDb = Double.MIN_VALUE;
        minDb = Double.MAX_VALUE;
        resetCurrentState();
        tvMaxDb.setText(LABEL_MAX_EMPTY);
        tvMinDb.setText(LABEL_MIN_EMPTY);
    }

    private void resetCurrentState() {
        decibelGaugeView.setCurrentDb(0);
        waveformView.clear();
        tvCurrentDb.setText(LABEL_CURRENT_RESET);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
