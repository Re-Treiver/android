package com.example.re_triever;

import android.content.pm.ActivityInfo;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnTouchListener, TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private boolean isRepeating = false;

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 언어 데이터가 없거나 지원되지 않을 때의 처리
            } else {
                speakOut("화면을 두번 터치하여 촬영을 하세요", true);
            }
        } else {
            // TTS 초기화 실패 시의 처리
        }
    }

    private void speakOut(String text, boolean isRepeating) {
        this.isRepeating = isRepeating;
        if (isRepeating) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private long lastTouchTime = 0;
    private Button mUploadButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tts = new TextToSpeech(this, this);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mSurfaceView = findViewById(R.id.cameraView);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceView.setOnTouchListener(this);

        mUploadButton = findViewById(R.id.uploadButton);
        mUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCamera != null) {
                    takePicture();
                }
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

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

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mCamera = openCamera(surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (mSurfaceHolder.getSurface() == null) {
            return;
        }

        try {
            // Only stop preview if the surface size has changed significantly
            if (width != mSurfaceView.getWidth() || height != mSurfaceView.getHeight()) {
                mCamera.stopPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    private void takePicture() {
        if (mCamera != null) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    File pictureFile = getOutputMediaFile();
                    if (pictureFile != null) {
                        try {
                            FileOutputStream fos = new FileOutputStream(pictureFile);

                            // 이미지 회전 처리
                            ExifInterface exifInterface = new ExifInterface(pictureFile.getAbsolutePath());
                            int orientation = exifInterface.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL);
                            int rotation = 90;
                            switch (orientation) {
                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    rotation = 180;
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    rotation = 270;
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    rotation = 0;
                                    break;
                            }
                            exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(rotation));

                            fos.write(getRotatedData(data, rotation));
                            fos.close();

                            // 이미지 파일을 서버로 업로드
                            FileUploadUtils.send2Server(pictureFile);

                            // TTS를 통해 메시지를 출력
                            speakOut("사진이 촬영되었습니다. 플라스틱 쓰레기입니다, 분류해서 버려주세요!", false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    // 사진 찍은 후에는 다시 미리보기를 시작한다
                    mCamera.startPreview();
                }
            });
        }
    }

    // 이미지 데이터 회전
    private byte[] getRotatedData(byte[] data, int rotation) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        Bitmap rotatedBitmap = getRotatedBitmap(bitmap, rotation);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        return outputStream.toByteArray();
    }

    // 이미지 회전하기
    private Bitmap getRotatedBitmap(Bitmap bitmap, int degrees) {
        if (bitmap == null) return null;
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.setRotate(degrees, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "re_triever");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator + "re_triever_photos_" + timeStamp + ".jpg");
    }

    private Camera openCamera(SurfaceHolder holder) {
        Camera camera = null;
        try {
            camera = Camera.open();
            if (camera != null) {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return camera;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(mSurfaceHolder);
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
