package com.capstone.textrecognizer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 1001;
    private PreviewView previewView;
    private Button captureButton;
    private TextView resultText;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private TextRecognizer textRecognizer;  
    private static String EXPORT_PATH;
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            takePhoto();
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            previewView = findViewById(R.id.preview_view);

            captureButton = findViewById(R.id.capture_button);

            resultText = findViewById(R.id.result_text);

            EXPORT_PATH = getFilesDir() + "/recognized_text.txt";
            Log.d(TAG, "EXPORT_PATH: " + EXPORT_PATH);

            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            cameraExecutor = Executors.newSingleThreadExecutor();

            if (!allPermissionsGranted()) {
                Log.d(TAG, "Requesting camera permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSION_REQUEST_CAMERA);
            } else {
                Log.d(TAG, "Camera permission already granted, starting camera");
                startCamera();
            }

            registerReceiver(broadcastReceiver, new IntentFilter("START_RECOGNITION"));

            captureButton.setOnClickListener(v -> takePhoto());

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    Log.d(TAG, "Camera provider obtained successfully");
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Error getting camera provider", e);
                    Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            Log.e(TAG, "Error in startCamera", e);
            Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        try {
            cameraProvider.unbindAll();

            Preview preview = new Preview.Builder().build();

            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            Camera camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture);

            Log.d(TAG, "Camera preview bound successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error binding preview", e);
            Toast.makeText(this, "Error binding camera preview: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
    void takePhoto() {
        try {
            if (imageCapture == null) {
                Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show();
                return;
            }

            imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy image) {
                            processImage(image);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException error) {
                            Log.e(TAG, "Error capturing image", error);
                            Toast.makeText(MainActivity.this,
                                    "Error taking photo: " + error.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error in takePhoto", e);
            Toast.makeText(this, "Error taking photo: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy image) {
        try {
            InputImage inputImage = InputImage.fromMediaImage(
                    image.getImage(),
                    image.getImageInfo().getRotationDegrees()
            );

            textRecognizer.process(inputImage)
                    .addOnSuccessListener(text -> {
                        resultText.setText(text.getText());
                        image.close();
                        try {
                            TextExporter.exportRecognizedText(text, EXPORT_PATH);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error processing image", e);
                        Toast.makeText(MainActivity.this,
                                "Error processing image: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        image.close();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in processImage", e);
            Toast.makeText(this, "Error processing image: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            image.close();
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (allPermissionsGranted()) {
                Log.d(TAG, "Camera permission granted, starting camera");
                startCamera();
            } else {
                Log.e(TAG, "Camera permission denied");
                Toast.makeText(this, "Camera permission is required for this app",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        unregisterReceiver(broadcastReceiver);
    }

}

class TextExporter {

    public static void exportRecognizedText(Text recognizedText, String EXPORT_PATH) throws IOException {
        StringBuilder output = new StringBuilder();

        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                output.append(line.getText()).append("\n");
            }
            output.append("\n");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(EXPORT_PATH))) {
            writer.write(output.toString());
        }
    }
}