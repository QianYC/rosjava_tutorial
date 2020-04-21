package nju.ycqian.phonenode;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;

import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends CompatROSActivity {
    private CameraControl cameraControl;
    /**
     * used to play shutter sound when taking picture
     */
    private MediaActionSound soundPlayer;
    private PhoneNode node;
    private CameraServer cameraServer;
    private StateClient stateClient;
    private TextureView view;
    private Switch modeSwitch;
    private Preview preview;
    private ImageAnalysis analysis;
    private ImageCapture capture;
    private ImagePublisher publisher;
    private byte[] analysisBuffer;
    private Size analysisSize = new Size(320, 240);

    public MainActivity() {
        super("PhoneNode", "PhoneNode");
        soundPlayer = new MediaActionSound();
        soundPlayer.load(MediaActionSound.SHUTTER_CLICK);
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        checkPermissions();

        try {
            cameraControl = CameraX.getCameraControl(CameraX.LensFacing.BACK);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        node = new PhoneNode(this);
        cameraServer = new CameraServer(this);
        stateClient = new StateClient();
        Log.i("Phone Node", "create");

        view = findViewById(R.id.textureView);
        view.setOnTouchListener((v, event) -> {
//            if (event.getAction() != MotionEvent.ACTION_UP) {
//                Log.i("view", "not focus");
//                return false;
//            }
            Log.i("view", "focus");
            MeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(this, CameraX.LensFacing.BACK,
                    v.getWidth(), v.getHeight());
            MeteringPoint point = factory.createPoint(event.getX(), event.getY());
            FocusMeteringAction action = FocusMeteringAction.Builder.from(point).build();
            cameraControl.startFocusAndMetering(action);
            return true;
        });

        modeSwitch = findViewById(R.id.modeSwitch);
        modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                buttonView.setText(R.string.static_mode);
                stateClient.changeState(true);
            } else {
                buttonView.setText(R.string.dynamic_mode);
                stateClient.changeState(false);
            }
        });

        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();
        preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(previewOutput -> view.setSurfaceTexture(previewOutput.getSurfaceTexture()));

        HandlerThread thread = new HandlerThread("imageAnalysis");
        thread.start();

        ImageAnalysisConfig analysisConfig = new ImageAnalysisConfig.Builder()
                .setTargetResolution(analysisSize)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .setCallbackHandler(new Handler(thread.getLooper()))
                .build();

        analysis = new ImageAnalysis(analysisConfig);
        analysis.setAnalyzer((image, rotation) -> {
            ImageProxy.PlaneProxy[] proxies = image.getPlanes();
            ByteBuffer yBuffer = proxies[0].getBuffer();
            ByteBuffer uBuffer = proxies[1].getBuffer();
            ByteBuffer vBuffer = proxies[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            if (analysisBuffer == null) {
                analysisBuffer = new byte[ySize + uSize + vSize];
            }

            //u and v are swapped
            yBuffer.get(analysisBuffer, 0, ySize);
            vBuffer.get(analysisBuffer, ySize, vSize);
            uBuffer.get(analysisBuffer, ySize + vSize, uSize);

            if (publisher != null) {
                publisher.onNewImage(analysisBuffer, analysisSize);
            }
        });

        ImageCaptureConfig captureConfig = new ImageCaptureConfig.Builder()
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();
        capture = new ImageCapture(captureConfig);

        CameraX.bindToLifecycle(this, preview, analysis, capture);
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        Log.i("Phone Node", "init");

        try {
            java.net.Socket socket = new java.net.Socket(getMasterUri().getHost(), getMasterUri().getPort());
            java.net.InetAddress local_network_address = socket.getLocalAddress();
            socket.close();
            NodeConfiguration nodeConfiguration =
                    NodeConfiguration.newPublic(local_network_address.getHostAddress(), getMasterUri());
            nodeMainExecutor.execute(node, nodeConfiguration);
            nodeMainExecutor.execute(cameraServer, nodeConfiguration);
            nodeMainExecutor.execute(stateClient, nodeConfiguration);
        } catch (IOException e) {
            // Socket problem
            Log.e("Phone Node", "socket error trying to get networking information from the master uri");
        }
    }

    private void checkPermissions() {
        PackageManager pm = this.getPackageManager();
        PackageInfo pkgInfo = null;
        try {
            pkgInfo = pm.getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String[] requestedPermissions = pkgInfo.requestedPermissions;

        for (String s : requestedPermissions) {
            Log.i("request", s);
        }

        //request permissions
        ActivityCompat.requestPermissions(this, requestedPermissions, 1);
    }

    public void setPublisher(ImagePublisher publisher) {
        this.publisher = publisher;
    }

    public void captureCallback(float x, float y) {
        Log.i("capture", "");
        // scale the metering point to phone preview
        float ax = view.getWidth() * x / 320, ay = view.getHeight() * y / 240;
        MeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(this, CameraX.LensFacing.BACK,
                view.getWidth(), view.getHeight());
        MeteringPoint point = factory.createPoint(ax, ay);
        FocusMeteringAction action = FocusMeteringAction.Builder.from(point).build();
        cameraControl.startFocusAndMetering(action);

        soundPlayer.play(MediaActionSound.SHUTTER_CLICK);
        capture.takePicture(createImageFile(), new ImageCapture.OnImageSavedListener() {
            @Override
            public void onImageSaved(@NonNull File file) {
                Log.i("capture", "save image success!");
            }

            @Override
            public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError,
                                @NonNull String message, @Nullable Throwable cause) {
                Log.e("capture", message);
            }
        });

    }

    /**
     * create a file to save the image
     *
     * @return
     */
    private File createImageFile() {
        File appDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "camera_app");

        if (!appDir.exists()) {
            Log.i("preview", "create camera_app dir");
            appDir.mkdirs();
        }

        Log.i("preview", "create image file");
        File image;
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        image = new File(appDir.getPath() + File.separator + "IMG_" + time + ".jpg");
        return image;
    }
}
