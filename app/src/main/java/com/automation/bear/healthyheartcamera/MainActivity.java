package com.automation.bear.healthyheartcamera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.ContactsContract;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Sampler;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import uk.me.berndporr.iirj.Butterworth;


public class MainActivity extends AppCompatActivity
        implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "HealtyHeartCamera";

    private Range<Integer>[] FPSRange;
    private Size imageDimension;
    private TextureView mTextureView;
    GraphView mGraph1;
    GraphView mGraph2;
    GraphView mGraph3;

    LineGraphSeries<DataPoint> mSeriesY = new LineGraphSeries<>();
    private long mX =1;

    Butterworth butterworthY = new Butterworth();

    LineGraphSeries<DataPoint> mSeriesYA = new LineGraphSeries<>();

    LineGraphSeries<DataPoint>  mSeriesSplineGraph = new LineGraphSeries<>();
    BarGraphSeries<DataPoint> mBarSeriesPeaksGraph = new BarGraphSeries<>();

    RenderScript rs;
    private Allocation in;
    private Allocation out;

    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;

    private static int WIDTH = 240;
    private static int HEIGHT = 320;

    private Button StartStopButton;
    private HRAnalyzer mHRHrAnalyzer;

    ListView peaksLstV;
    ProgressBar progressBar;
    TextView lastPeakTextV;

    List<MeasurementLog> measurementLogs = new ArrayList<>();

    public class MeasurementLog{
        public double Yvalue;
        public long timeStamp;
        MeasurementLog(double Yvalue, long timeStamp){
            this.Yvalue = Yvalue;
            this.timeStamp = timeStamp;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //createAllocation();
        setContentView(R.layout.activity_main);
        findViewById(R.id.button).setOnClickListener(this);
        findViewById(R.id.buttonRefresh).setOnClickListener(this);
        StartStopButton = findViewById(R.id.button);
        mTextureView = findViewById(R.id.texture);
        peaksLstV = findViewById(R.id.peakTimeLstV);
        progressBar = findViewById(R.id.progressBar);
        progressBar.stopNestedScroll();
        lastPeakTextV = findViewById(R.id.lastPeakTextV);
        assert mTextureView != null;
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        mGraph1 = (GraphView) findViewById(R.id.graph1);
        mGraph1.getViewport().setScalable(true);
        mGraph1.getViewport().setScrollable(true);
        mGraph1.getViewport().setMaxX(120);
        mGraph1.addSeries(mSeriesYA);

        mGraph2 = (GraphView) findViewById(R.id.graph2);
        mGraph2.getViewport().setScalable(true);
        mGraph2.getViewport().setScrollable(true);
        mGraph2.getViewport().setMaxX(120);
        mGraph2.addSeries(mSeriesY);

        mGraph3 = (GraphView) findViewById(R.id.graph3);
        mGraph3.getViewport().setScalable(true);
        mGraph3.getViewport().setScrollable(true);
        mGraph3.addSeries(mSeriesSplineGraph);

        lastPeakTextV.setMovementMethod(new ScrollingMovementMethod());

//        BarGraphSeries<DataPoint> series = new BarGraphSeries<>(new DataPoint[] {
//                new DataPoint(0, -1),
//                new DataPoint(1, 5),
//                new DataPoint(2, 3),
//                new DataPoint(3, 2),
//                new DataPoint(4, 6)
//        });
//        mGraph3.addSeries(series);
//        mGraph3.getViewport().setScalable(true);
//        mGraph3.getViewport().setScrollable(true);

        mHRHrAnalyzer = new HRAnalyzer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button: {
                if(StartStopButton.getText().equals("Start")){
                    StartStopButton.setEnabled(false);
                    startMeasurement();
                    StartStopButton.setText("Stop");
                    StartStopButton.setEnabled(true);
                }
                else
                {
                    StartStopButton.setEnabled(false);
                    stopMeasurement();
                    StartStopButton.setText("Start");
                    StartStopButton.setEnabled(true);
                }
                break;
            }
            case R.id.buttonRefresh:{
                double graphWidth = mSeriesSpline[mSeriesSpline.length-1].getX() - mSeriesSpline[0].getX();
                if(graphWidth > 5000) graphWidth = graphWidth - 5000;
                mGraph3.getViewport().setMinX(0);
                mGraph3.getViewport().setMaxX(mSeriesSpline[mSeriesSpline.length-1].getX());
            }
        }
    }

    private boolean startMeasurement(){
        mX=1;
        last = 0;
        mHRHrAnalyzer.flushData();

        addressOfLastSplineCalculation = 0;
        DataPoint[] values = new DataPoint[1];
        values[0] =  new DataPoint(0,0);

        //mGraph1.removeAllSeries();
        mSeriesYA.resetData(values);

        //mGraph1.addSeries(mSeriesYA);
        //mGraph1.getViewport().setMaxX(0);
        //mGraph1.getViewport().setMaxX(120);

        //mGraph2.removeAllSeries();
        mSeriesY.resetData(values);
        //mGraph2.addSeries(mSeriesY);
        //mGraph2.getViewport().setMaxX(120);

        mSeriesSplineGraph.resetData(values);
        mGraph3.removeAllSeries();
        mGraph3.clearSecondScale();


        butterworthY = new Butterworth();
        butterworthY.bandPass(4,29, 2.16, 2);
        for(int i = 1; i<=200; i++){
            butterworthY.filter(240);
        }
        openCamera(288, 384);

        return true;
    }


    DataPoint[] mSeriesSpline;

    private boolean stopMeasurement(){

        closeCamera();
        Log.i(TAG,"Measurement logs: ");

        String filename = "myfile";
        FileOutputStream outputStream;

        try {
            outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(("Yvalue = [").getBytes());
            for(MeasurementLog a : measurementLogs){
                outputStream.write((a.Yvalue + ",").getBytes());
            }
            outputStream.write(("];").getBytes());

            outputStream.write(("/ntimeStamp = [").getBytes());
            for(MeasurementLog a : measurementLogs){
                outputStream.write((a.timeStamp + ",").getBytes());
            }
            outputStream.write(("];").getBytes());

            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        measurementLogs.clear();

        mSeriesSpline = mHRHrAnalyzer.getPolyPlot(0, mHRHrAnalyzer.mMeasurementLst.size());

        final DataPoint[] mSeriesPeaks = new DataPoint[mHRHrAnalyzer.peakTimeLst.size()];
        for(int i = 0; i < mHRHrAnalyzer.peakTimeLst.size(); i++){
            mSeriesPeaks[i] = new DataPoint(mHRHrAnalyzer.peakTimeLst.get(i).getX(),0.2);
        }
        mBarSeriesPeaksGraph.setDataWidth(5.0);
        //mBarSeriesPeaksGraph.setColor();
        mGraph3.addSeries(mBarSeriesPeaksGraph);

        thisActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSeriesSplineGraph.resetData(mSeriesSpline);
                mBarSeriesPeaksGraph.resetData(mSeriesPeaks);
                double graphWidth = mSeriesSpline[mSeriesSpline.length-1].getX() - mSeriesSpline[0].getX();
                if(graphWidth > 5000) {
                    graphWidth = graphWidth - 5000;
                }else{
                    graphWidth = 0;
                }

                mGraph3.getViewport().setMinX(graphWidth);
                mGraph3.getViewport().setMaxX(mSeriesSpline[mSeriesSpline.length-1].getX());
                mGraph3.getViewport().setMinY(mSeriesSplineGraph.getLowestValueY());
                mGraph3.getViewport().setMaxY(mSeriesSplineGraph.getHighestValueY());
            }
        });

        for(int i=1; i < mHRHrAnalyzer.peakTimeLst.size(); i++){
            lastPeakTextV.setText(lastPeakTextV.getText() + "\n" +
                    (mHRHrAnalyzer.peakTimeLst.get(i).getX()-mHRHrAnalyzer.peakTimeLst.get(i-1).getX()) + "ms");
        }


        return true;
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            //openCamera(288, 384);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };


    private ImageReader mImageReader;
    private long lastFrameTime;
    double ButterY = 0;
    double yAverage = 0;
    double last = 0;
    int addressOfLastSplineCalculation = 0;
    //DataPoint splinePointsToAddArr[] = new DataPoint[];
    double lastButterY = 0;
    boolean newSeries = false;
    boolean okPutSeries = false;
    boolean seriesReady = false;
    List<DataPoint> splinePointsToAddLst = new ArrayList<>();

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if(image!=null){

                long timeBetweenFrames = image.getTimestamp() - lastFrameTime;
                lastFrameTime = image.getTimestamp();
                long computeTimer = System.currentTimeMillis();


                yAverage = countYMeanfromYUV(image.getPlanes()[0],image.getWidth(), image.getHeight());
                measurementLogs.add(new MeasurementLog(yAverage, image.getTimestamp()));
                if(yAverage < 0) yAverage = -yAverage;
                double time = (timeBetweenFrames/1000000.0);
                if(mX >1) last = time + last;   //pominąć czas pierwszej ramki

                ButterY = mHRHrAnalyzer.addData(new Measurement(last,yAverage));

                //double previousValue = mHRHrAnalyzer.mMeasurementLst.get(mHRHrAnalyzer.mMeasurementLst.size()-2).buttValue;
                int newData = mHRHrAnalyzer.mMeasurementLst.size() - addressOfLastSplineCalculation;


                if(lastButterY < 0 && ButterY >= 0 && newData > 3){
                    newSeries = true;
                }
                if(okPutSeries == true){
                    okPutSeries = false;
                    DataPoint splinePointsToAddArr[] = mHRHrAnalyzer.getPolyPlot(addressOfLastSplineCalculation +1,
                                                   mHRHrAnalyzer.mMeasurementLst.size());
//                    splinePointsToAddLst = mHRHrAnalyzer.getPolyPlotLst(addressOfLastSplineCalculation + 1,
//                                                           mHRHrAnalyzer.mMeasurementLst.size());

                    mSeriesSplineGraph.resetData(splinePointsToAddArr);
                    addressOfLastSplineCalculation = mHRHrAnalyzer.mMeasurementLst.size();
                    Log.i(TAG, "Przejscie przez zero od dolu");
                    seriesReady = true;
                }
                lastButterY = ButterY;

                mX++;
                thisActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSeriesY.appendData(new DataPoint(mX,ButterY),true,120);
                        mSeriesYA.appendData(new DataPoint(mX,yAverage),true,120);

                        if(newSeries){
                            newSeries = false;
                            mGraph3.removeAllSeries();
                            mGraph3.getViewport().calcCompleteRange();
                            okPutSeries = true;
                        }

                        if(seriesReady){
                            seriesReady = false;
                            mGraph3.addSeries(mSeriesSplineGraph);
                            mGraph3.getViewport().setMinX(mSeriesSplineGraph.getLowestValueX());
                            mGraph3.getViewport().setMaxX(mSeriesSplineGraph.getHighestValueX());
                            mGraph3.getViewport().setMinY(mSeriesSplineGraph.getLowestValueY());
                            mGraph3.getViewport().setMaxY(mSeriesSplineGraph.getHighestValueY());
                            lastPeakTextV.setText(mHRHrAnalyzer.getLastPeak());
                        }
//                        if(plotSpline){
//                            Log.i(TAG, "Bendem rysowal: " + splinePointsToAddLst.size());
//                            for(int i = 0 ; i < splinePointsToAddLst.size(); i++){
//                                mSeriesSplineGraph.appendData(splinePointsToAddLst.get(i),true,20000, false);
//                            }
//                            double graphWidth = splinePointsToAddLst.get(splinePointsToAddLst.size()-1).getX();
//                            if(graphWidth > 5000) {
//                                graphWidth = graphWidth - 5000;
//                            }else{
//                                graphWidth = 0;
//                            }
//
//                            mGraph3.getViewport().setMinX(graphWidth);
//                            mGraph3.getViewport().setMaxX(splinePointsToAddLst.get(splinePointsToAddLst.size()-1).getX());
//                            Log.i(TAG, "Wyrysowalem wykres");
//                            //splinePointsToAddLst = new ArrayList<>();
//                            plotSpline = false;
//                        }
                    }
                });

                Log.i(TAG, "T: " + timeBetweenFrames/1000000 +"ms,"+
                        " Comp: " + (System.currentTimeMillis()-computeTimer) + "ms" +
                        "  H value = " + FrameHAvg);
                image.close();

            }
        }
    };




    private Activity thisActivity = this;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private boolean mFlashSupported;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        Activity activity = this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }


    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                FPSRange = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                imageDimension = map.getOutputSizes(SurfaceTexture.class)[13];

                mImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888
                        , /*maxImages*/30);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }




    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = thisActivity;
            if (null != activity) {
                activity.finish();
            }
        }

    };



    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPSRange[7]);

            //mPreviewRequestBuilder.addTarget(in.getSurface());

            mPreviewRequestBuilder.addTarget(surface);

            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());


            //mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(/*in.getSurface(),*/surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                               // setAutoFlash(mPreviewRequestBuilder);

                                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private int mState = STATE_PREVIEW;


    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    /*Image image = mImageReader.acquireLatestImage();
                    if(image != null){
                        Log.i(TAG, "Eureka!");
                    }else{
                        Log.i(TAG, "Lipa :(");
                    }*/
                    break;
                }
                case STATE_WAITING_LOCK: {
//                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
//                    if (afState == null) {
//                        captureStillPicture();
//                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
//                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
//                        // CONTROL_AE_STATE can be null on some devices
//                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                        if (aeState == null ||
//                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
//                            mState = STATE_PICTURE_TAKEN;
//                            captureStillPicture();
//                        } else {
//                            runPrecaptureSequence();
//                        }
//                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
//                    // CONTROL_AE_STATE can be null on some devices
//                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                    if (aeState == null ||
//                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
//                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
//                        mState = STATE_WAITING_NON_PRECAPTURE;
//                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
//                    // CONTROL_AE_STATE can be null on some devices
//                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
//                        mState = STATE_PICTURE_TAKEN;
//                        captureStillPicture();
//                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };




    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private void showToast(final String text) {
        final Activity activity = this;
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static final String FRAGMENT_DIALOG = "dialog";
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getSupportFragmentManager()/*getChildFragmentManager()*/, FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }


    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    private double countYMeanfromYUV(Image.Plane planeY, int mWidth, int mHeight){
        ByteBuffer yPlane = planeY.getBuffer();
        int[] yContainer = new int[76800];//[38400];
        int[] yContainer2 = new int[76800];
        final int total = yPlane.capacity();

        int yPos = 0;
        for (int i = 0; i < mHeight; i++) {
            for (int j = 0; j < mWidth; j++) {
                if (yPos >= total) break;
                yContainer[yPos] = yPlane.get(yPos++) & 0xff;
            }
        }

        int a = yPlane.get(80000);
        int b = yPlane.get(70000);
        Log.i(TAG,"yPlane length: " + yPos);

        return Arrays.stream(yContainer).average().getAsDouble();

    }

    //private int mHeight = 200;
    double FrameRAvg = 0;
    double FrameHAvg = 0;

    private void getRGBIntFromPlanes(Image.Plane[] planes, int mWidth, int mHeight) {
        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();
        ByteBuffer vPlane = planes[2].getBuffer();

        int[] mRgbBuffer = new int[19540];  //240x320  /4
        double[] mHBuffer = new double[19540];
        int bufferIndex = 0;
        final int total = yPlane.capacity();
        final int uvCapacity = uPlane.capacity();
        //final int width = planes[0].getRowStride();


        int yPos = 0;
        for (int i = 0; i < mHeight; i=i+4) {
            int uvPos = (i >> 1) * mWidth;

            for (int j = 0; j < mWidth; j++) {
                if (uvPos >= uvCapacity-1)
                    break;
                if (yPos >= total)
                    break;

                final int y1 = yPlane.get(yPos++) & 0xff;

            /*
              The ordering of the u (Cb) and v (Cr) bytes inside the planes is a
              bit strange. The _first_ byte of the u-plane and the _second_ byte
              of the v-plane build the u/v pair and belong to the first two pixels
              (y-bytes), thus usual YUV 420 behavior. What the Android devs did
              here (IMHO): just copy the interleaved NV21 U/V data to two planes
              but keep the offset of the interleaving.
             */
                final int u = (uPlane.get(uvPos) & 0xff) - 128;
                final int v = (vPlane.get(uvPos+1) & 0xff) - 128;
                if ((j & 1) == 1) {
                    uvPos += 2;
                }

                // This is the integer variant to convert YCbCr to RGB, NTSC values.
                // formulae found at
                // https://software.intel.com/en-us/android/articles/trusted-tools-in-the-new-android-world-optimization-techniques-from-intel-sse-intrinsics-to
                // and on StackOverflow etc.
                final int y1192 = 1192 * y1;
//
//                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
//                //g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
//                //b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);
//
//                mRgbBuffer[bufferIndex++] = ((r << 6) & 0xff0000) |
//                        ((g >> 2) & 0xff00) |
//                        ((b >> 10) & 0xff);

                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                int rr =  ((r >> 10) & 0xff);
                int gg = ((g >> 10) & 0xff);
                int bb = ((b >> 10) & 0xff);

                mHBuffer[bufferIndex++] = RGBtoH(rr,gg,bb);

                mRgbBuffer[bufferIndex] = r;

          }//end rows?
        }//end columns?

        FrameHAvg = Arrays.stream(mHBuffer).average().getAsDouble();
        FrameRAvg = Arrays.stream(mRgbBuffer).average().getAsDouble();
    }

    private double RGBtoH(int R, int G, int B){
        double min = Math.min(Math.min(R, G), B);
        double max = Math.max(Math.max(R, G), B);
        double delta = max - min;


        double H = max;
        if(delta == 0){
            H = 0;
        }else{
            double delR = ( ( ( max - R ) / 6.0 ) + ( delta / 2.0 ) ) / delta;
            double delG = ( ( ( max - G ) / 6.0 ) + ( delta / 2.0 ) ) / delta;
            double delB = ( ( ( max - B ) / 6.0 ) + ( delta / 2.0 ) ) / delta;

            if(R == max){
                H = delB - delG;
            }else if(G== max){
                H = (1.0/3.0) + delR - delB;
            }else if(B == max){
                H = (2.0/3.0) + delG - delR;
            }

            if(H < 0) H += 1;
            if(H > 1) H -= 1;
        }
        return H;
    }

}
