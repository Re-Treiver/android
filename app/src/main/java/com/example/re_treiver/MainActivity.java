package com.example.re_treiver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.view.TextureView;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Camera mCamera;
    private TextureView mTextureView;
    private long lastTouchTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mTextureView = findViewById(R.id.camera_preview_textureview);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            private long lastTouchTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    long now = System.currentTimeMillis();
                    if (now - lastTouchTime < 500) {
                        takePicture();
                    }
                    lastTouchTime = now;
                }
                return true;
            }
        });

    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        requestCameraPermission();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        int m_resWidth;
        int m_resHeight;
        m_resHeight = mCamera.getParameters().getPictureSize().height;
        m_resWidth = mCamera.getParameters().getPictureSize().width;
        Camera.Parameters parameters = mCamera.getParameters();
        m_resWidth = 1280;
        m_resHeight = 720;
        parameters.setPictureSize(m_resWidth, m_resHeight);
        mCamera.setParameters(parameters);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        releaseCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        try {
            mCamera = Camera.open();
            mCamera.setPreviewTexture(mTextureView.getSurfaceTexture());
            // 자동 초점 모드 설정
            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            mCamera.setParameters(params);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private void takePicture() {
        if (mCamera != null) {
            mCamera.takePicture(null, null, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
