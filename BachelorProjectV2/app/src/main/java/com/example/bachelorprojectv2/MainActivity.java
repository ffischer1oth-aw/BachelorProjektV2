package com.example.bachelorprojectv2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener{

    //start: constants
    String TIMESTAMP = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    long INTERVAL = 6750;
    int ABTASTRATE = 100000;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1488;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean ISCAPTURING = false;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }
    //end: constants

    //start: variables
    private File filepath;
    private File file;
    private String GNSSRawData;
    private String GNSSCoordinates;
    private String Orientation;
    private String AccelerometerData;
    private String MagnetometerData;
    private String GyroscopData;
    private String GravityData;
    private Size imageDimension;
    //end: variables

    //start: objects
    Timer timer = new Timer();
    private LocationManager locationManager;
    private GnssMeasurementsEvent.Callback gnssCallback;
    private SensorManager sensorManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Handler handler;
    private HandlerThread handlerThread;
    //end: objects

    //start: UI Elements
    private Button CAPTUREBUTTON;
    private TextureView TEXTUREVIEW;
    //end: UI Elements

    //start: onXYZ Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TEXTUREVIEW = (TextureView)findViewById(R.id.textureView);
        assert TEXTUREVIEW != null;
        TEXTUREVIEW.setSurfaceTextureListener(textureListener);
        CAPTUREBUTTON = (Button)findViewById(R.id.startButton);
        CAPTUREBUTTON.setOnClickListener(view -> {
            if (!ISCAPTURING){
                startRoutine();
            }
            else{
                stopRoutine();
            }
        });

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        Sensor ACCELEROMETER;
        Sensor MAGNETOMETER;
        Sensor GYROSCOPE;
        Sensor ROTATIONVECTOR;
        Sensor GRAVITOMETER;
        ACCELEROMETER = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        MAGNETOMETER = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        GYROSCOPE = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        ROTATIONVECTOR = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        GRAVITOMETER = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorManager.registerListener((SensorEventListener) this, ACCELEROMETER, ABTASTRATE);
        sensorManager.registerListener((SensorEventListener) this, MAGNETOMETER, ABTASTRATE);
        sensorManager.registerListener((SensorEventListener) this, GYROSCOPE, ABTASTRATE);
        sensorManager.registerListener((SensorEventListener) this, ROTATIONVECTOR, ABTASTRATE);
        sensorManager.registerListener((SensorEventListener) this, GRAVITOMETER, ABTASTRATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "You can't use the camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // Call the superclass implementation
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission granted, register GNSS measurements callback
                try {
                    //locationManager.registerGnssMeasurementsCallback(gnssCallback);
                }catch (SecurityException e){
                    Toast.makeText(this, "Error location Permissions: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                // Location permission denied, show a message or handle it gracefully
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
        if (locationManager != null && gnssCallback != null) {
            try {
                locationManager.unregisterGnssMeasurementsCallback(gnssCallback);
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        if (ISCAPTURING){
            stopPhotoCapture();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        CAPTUREBUTTON.setText("Start");
        startBackgroundThread();
        if(TEXTUREVIEW.isAvailable())
            openCamera();
        else
            TEXTUREVIEW.setSurfaceTextureListener(textureListener);
        if (checkGnssSupport()){
            if (checkLocationPermission()) {
                //Toast.makeText(this, "Location permission is granted, register GNSS measurements callback", Toast.LENGTH_SHORT).show();
                registerGNSS();
                //getLocationUpdates();

            } else {
                Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
                requestLocationPermission();
            }} else {
            Toast.makeText(this, "GNSS measurements are not supported on this device.", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (ISCAPTURING) {
            float[] rotationMatrix;
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float[] AccelerometerData = event.values;
                orientationToCSV("Accelerometer", AccelerometerData);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                float[] MagnetometerData = event.values;
                orientationToCSV("Magnetometer", MagnetometerData);
            } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                float[] GravityData = event.values;
                orientationToCSV("Gravity", GravityData);
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                float[] GyroscopeData = event.values;
                orientationToCSV("Gyroscop", GyroscopeData);
            } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                rotationMatrix = new float[16];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                float[] orientationValues = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientationValues);
                orientationToCSV("Orientation", orientationValues);
            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    //end: onXYZ Methods

    //start: general functions
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {}
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}
    };
    //end: general functions

    //start: camera functions
    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            String cameraID = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraID,stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
        }
    };
    private void createCameraPreview() {
        try{
            SurfaceTexture texture = TEXTUREVIEW.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f); // Focus at infinity
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        //captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void takePicture() {
        // Check if cameraDevice is null, and return immediately if it is
        if (cameraDevice == null)
            return;
        // Get the CameraManager system service
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Retrieve characteristics of the camera (such as its resolution)
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] JPEGSIZE = null;
            // Check if characteristics is not null, and retrieve JPEG output sizes
            if (characteristics != null)
                JPEGSIZE = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            // Set default width and height for the image
            int WIDTH = 640;
            int HEIGHT = 480;
            // If possible JPEG sizes are available, use the width and height of the first (largest) option
            if (JPEGSIZE != null && JPEGSIZE.length > 0) {
                WIDTH = JPEGSIZE[0].getWidth();
                HEIGHT = JPEGSIZE[0].getHeight();
            }

            // Create an ImageReader with the obtained width and height, and format as JPEG
            final ImageReader reader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.JPEG, 1);

            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(TEXTUREVIEW.getSurfaceTexture()));

            // Build a request for capturing a still image
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());

            // Set Autofocus to off and focus distance to infinity
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
            //captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 100);
            //captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
            // Set JPEG quality to maximum
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

            // Check orientation based on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            // Generate a file name based on the current date and time
            File outputDirectory = getOutputDirectory();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "IMG_" + sdf.format(new Date()) + ".jpg";
            file = new File(outputDirectory, fileName);

            // Listener for when an image is available, which will save the image to the file system
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try {
                        // Acquire the latest image, get its byte buffer, and save it
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        // Close the image if it's not null
                        if (image != null)
                            image.close();
                    }
                }
                // Save the byte array to a file
                private void save(byte[] bytes) throws IOException {
                    OutputStream outputStream = null;
                    try {
                        Toast.makeText(MainActivity.this, "Saving Picture", Toast.LENGTH_SHORT).show();
                        outputStream = Files.newOutputStream(file.toPath());
                        outputStream.write(bytes);
                    } finally {
                        // Close the output stream if it's not null
                        if (outputStream != null)
                            outputStream.close();
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, handler);
            // Callback for the capture session, which recreates the camera preview after a picture is taken
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };
            // Create a camera capture session with the output surface
            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        // Capture the image
                        cameraCaptureSession.capture(captureBuilder.build(), captureListener, handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // Handle configuration failure
                }
            }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    //end: camera functions

    //start: gnss raw functions
    public void registerGNSS(){
        gnssCallback = new GnssMeasurementsEvent.Callback() {
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                runOnUiThread(() -> {
                    if (ISCAPTURING) {
                        List<GnssMeasurement> measurements = new ArrayList<>(event.getMeasurements());
                        gnssMeasurementsToCSV(measurements,event.getClock());
                    }
                });
            }
        };
        try {
            locationManager.registerGnssMeasurementsCallback(gnssCallback);
        }catch (SecurityException e){
            Toast.makeText(this, "Error location Permissions: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }
    private boolean checkGnssSupport() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }
    //end: gnss raw functions

    //start: gnss coordinates functions
    private void getLocationUpdates() {
        if (locationManager != null) {
            // Define a location listener to receive updates
            LocationListener locationListener = location -> updateLocationViews(location);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
            } catch (SecurityException e) {
                Toast.makeText(this, "Error requesting location updates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void updateLocationViews(Location location) {
        if (ISCAPTURING) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            double altitude= location.getAltitude();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String Time = sdf.format(new Date());
            //Toast.makeText(this, "Coordinates measured", Toast.LENGTH_SHORT).show();
            GNSSCoordinates = GNSSCoordinates +Time+"," +latitude+","+longitude+","+altitude+"\n";
        }
    }
    //end: gnss coordinates functions

    //start: saving functions
    private File getOutputDirectory() {
        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        String folderName = "BachelorProjektV2_" + TIMESTAMP;
        File appDir = new File(downloadDir, folderName);
        appDir.mkdirs();
        filepath = appDir;
        return appDir;
    }
    public void orientationToCSV(String Type,float [] values){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String string = sdf.format(new Date())+",";
        for (int i = 0; i < values.length; i++) {
            string = string + Float.toString(values[i]);
            if (i <values.length-1){
                string = string +",";
            }
            else {
                string = string +"\n";
            }
        }
        if (Type == "Orientation"){
            Orientation=Orientation+string;
        }
        else if (Type == "Accelerometer"){
            AccelerometerData=AccelerometerData+string;
        }
        else if (Type == "Magnetometer"){
            MagnetometerData=MagnetometerData+string;
        }
        else if (Type == "Gyroscop"){
            GyroscopData=GyroscopData+string;
        }
        else if (Type == "Gravity"){
            GravityData=GravityData+string;
        }
    }
    private void saveOrientationToCSV() {
        String [] Types =new String[] {Orientation,AccelerometerData,MagnetometerData,GyroscopData,GravityData};
        String header="";
        String content="";
        String csvData="";
        String child="";
        boolean append = false;
        for (int i = 0; i < Types.length; i++) {
            if (i==0){
                header="Time,Angle1,Angle2,Angle3\n";
                content=Orientation;
                child="DATA_"+ TIMESTAMP +"_Euler-Angles.csv";
            }
            else if (i==1){
                header="Time,Accelerometer1,Accelerometer2,Accelerometer3\n";
                content=AccelerometerData;
                child="DATA_"+ TIMESTAMP +"_Accelerometer.csv";
            }
            else if (i==2){
                header="Time,Magnetometer1,Magnetometer2,Magnetometer3\n";
                content=MagnetometerData;
                child="DATA_"+ TIMESTAMP +"_Magnetometer.csv";
            }
            else if (i==3){
                header="Time,Gyroscop1,Gyroscop2,Gyroscop3\n";
                content=GyroscopData;
                child="DATA_"+ TIMESTAMP +"_Gyroscop.csv";
            }
            else if (i==4){
                header="Time,Gravity1,Gravity2,Gravity3\n";
                content=GravityData;
                child="DATA_"+ TIMESTAMP +"_Gravity.csv";
            }
            File csvFile = new File(filepath, child);
            if (csvFile.exists()) {
                csvData = content;
                append = true;
            }
            else {
                csvData = header + content;
            }
            try {
                FileWriter writer = new FileWriter(csvFile,append);
                writer.write(csvData);
                writer.close();
                if (i==0){Orientation="";}
                else if (i==1){AccelerometerData="";}
                else if (i==2){MagnetometerData="";}
                else if (i==3){GyroscopData="";}
                else if (i==4){GravityData="";}
            } catch (IOException e) {
                Toast.makeText(this, "Error saving coordinates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void gnssMeasurementsToCSV(List<GnssMeasurement> measurements, GnssClock clock) {
        StringBuilder csvData = new StringBuilder();
        String Time = String.valueOf(clock.getTimeNanos());
        String BiasNanos = String.valueOf(clock.getBiasNanos());
        String FullBiasNanos = String.valueOf(clock.getFullBiasNanos());
        String TimeUncertaintyNanos = String.valueOf(clock.getTimeUncertaintyNanos());
        String LeapSecond = String.valueOf(clock.getLeapSecond());
        for (GnssMeasurement measurement : measurements) {
            csvData.append(Time+",");
            csvData.append(BiasNanos+",");
            csvData.append(FullBiasNanos+",");
            csvData.append(TimeUncertaintyNanos+",");
            csvData.append(LeapSecond+",");
            csvData.append(measurement.getAccumulatedDeltaRangeMeters()).append(",");
            csvData.append(measurement.getAccumulatedDeltaRangeState()).append(",");
            csvData.append(measurement.getAccumulatedDeltaRangeUncertaintyMeters()).append(",");
            csvData.append(measurement.getCarrierCycles()).append(",");
            csvData.append(measurement.getCarrierFrequencyHz()).append(",");
            csvData.append(measurement.getCn0DbHz()).append(",");
            csvData.append(measurement.getCodeType()).append(",");
            csvData.append(measurement.getConstellationType()).append(",");
            csvData.append(measurement.getMultipathIndicator()).append(",");
            csvData.append(measurement.getPseudorangeRateMetersPerSecond()).append(",");
            csvData.append(measurement.getPseudorangeRateUncertaintyMetersPerSecond()).append(",");
            csvData.append(measurement.getReceivedSvTimeNanos()).append(",");
            csvData.append(measurement.getReceivedSvTimeUncertaintyNanos()).append(",");
            csvData.append(measurement.getSnrInDb()).append(",");
            csvData.append(measurement.getState()).append(",");
            csvData.append(measurement.getSvid()).append(",");
            csvData.append(measurement.getTimeOffsetNanos()).append("\n");
        }

        GNSSRawData = GNSSRawData + csvData.toString();
    }
    private void saveGNSSMeasuremntsToCSV() {
        String header = "TimeNanos,BiasNanos,FullBiasNanos,TimeUncertaintyNanos,LeapSecond,AccumulatedDeltaRangeMeters,AccumulatedDeltaRangeState,AccumulatedDeltaRangeUncertaintyMeters,CarrierCycles,CarrierFrequencyHz,Cn0DbHz,CodeType,ConstellationType,MultipathIndicator,PseudorangeRateMetersPerSecond,PseudorangeRateUncertaintyMetersPerSecond,ReceivedSvTimeNanos,ReceivedSvTimeUncertaintyNanos,SnrInDb,State,Svid,TimeOffsetNanos\n";
        String csvData = "";
        boolean append = false;
        File csvFile = new File(filepath, "DATA_"+ TIMESTAMP +"_GNSS-Raw-Measurements.csv");
        if (csvFile.exists()) {
            csvData = GNSSRawData;
            append = true;
        }
        else {
            csvData = header + GNSSRawData;
        }
        try {
            FileWriter writer = new FileWriter(csvFile,append);
            writer.write(csvData);
            writer.close();
            GNSSRawData ="";
        } catch (IOException e) {
            Toast.makeText(this, "Error saving measurements: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void saveCoordinatesToCSV() {
        String header = "Time,latitude,longitude,altitude\n";
        String csvData;
        boolean append = false;
        File csvFile = new File(filepath, "DATA_"+ TIMESTAMP +"_GNSS-Coordinates.csv");
        if (csvFile.exists()) {
            csvData = GNSSCoordinates;
            append = true;
        }
        else {
            csvData = header + GNSSCoordinates;
        }
        try {
            FileWriter writer = new FileWriter(csvFile, append);
            writer.write(csvData);
            writer.close();
            GNSSCoordinates = "";
        } catch (IOException e) {
            Toast.makeText(this, "Error saving coordinates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    //end: saving functions

    //start: handling the routine and the thread
    private void stopBackgroundThread() {
        handlerThread.quitSafely();
        try{
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void startBackgroundThread() {
        handlerThread = new HandlerThread("Camera Background");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }
    private void startPhotoCapture(){
        timer = new Timer();
        timer.schedule(new scheduledPhotoCapture(), 0, INTERVAL);
    }
    private void stopPhotoCapture() {
            timer.cancel();
    }
    class scheduledPhotoCapture extends TimerTask {
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takePicture();
                    saveGNSSMeasuremntsToCSV();
                    saveCoordinatesToCSV();
                    saveOrientationToCSV();
                }
            });
        }
    }
    private void startRoutine(){
        CAPTUREBUTTON.setText("Stop");
        ISCAPTURING =true;
        TIMESTAMP = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        GNSSRawData ="";
        GNSSCoordinates ="";
        Orientation="";
        AccelerometerData="";
        MagnetometerData="";
        GyroscopData="";
        GravityData="";
        startPhotoCapture();
        if (checkGnssSupport()){
            if (checkLocationPermission()) {
                //Toast.makeText(this, "Location permission is granted, register GNSS measurements callback", Toast.LENGTH_SHORT).show();
                registerGNSS();
                getLocationUpdates();

            } else {
                Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
                requestLocationPermission();
            }} else {
            Toast.makeText(this, "GNSS measurements are not supported on this device.", Toast.LENGTH_SHORT).show();
        }
    }
    private void stopRoutine(){
        CAPTUREBUTTON.setText("Start");
        ISCAPTURING = false;
        stopPhotoCapture();
        saveGNSSMeasuremntsToCSV();
        saveCoordinatesToCSV();
        saveOrientationToCSV();
        Toast.makeText(this, "Saving Measurements", Toast.LENGTH_SHORT).show();
        if (locationManager != null && gnssCallback != null) {
            try {
                locationManager.unregisterGnssMeasurementsCallback(gnssCallback);
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    //end: handling the routine and the thread
}
