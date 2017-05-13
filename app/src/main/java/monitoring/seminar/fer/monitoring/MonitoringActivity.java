package monitoring.seminar.fer.monitoring;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.MultiDetector;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class MonitoringActivity extends AppCompatActivity {
    private final static String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO
    };

    private final Map<String, Detector<?>> CAMERA_TRIGGER_DETECTORS = new HashMap<>();
    private final Map<String, SoundTrigger> SOUND_TRIGGERS = new HashMap<>();
    private final Map<String, Action> ACTIONS = new HashMap<>();

    private static final String TAG = "Monitoring";
    public static final String CAMERA_TRIGGERED_PREFS = "CAMERA_TRIGGERED_PREFS";
    public static final String SOUND_TRIGGERED_PREFS = "SOUND_TRIGGERED_PREFS";

    private static int audioRecorderBufferSize = 4 * AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_DEFAULT,
            AudioFormat.ENCODING_PCM_16BIT);

    private Button mBtConfigure;
    private Button mBtStart;
    private Button mBtStop;

    private boolean mIsMonitoringStarted = false;
    private ScheduledFuture mScheduledPoolFuture;

    private CameraSource mCameraSource;
    private AudioRecord mAudioRecorder;
    private SoundSource mSoundSource;

    private GoogleApiClient mGoogleApiClient;
    private ScheduledExecutorService mScheduledPool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CAMERA_TRIGGER_DETECTORS.put(ActionTriggers.FACE_DETECTED,
                new FaceDetector.Builder(getApplicationContext()).build());

        SOUND_TRIGGERS.put(ActionTriggers.NOISE_DETECTED,
                new NoiseTrigger(1000, 45));

        ACTIONS.put(Actions.SAVE_PICTURE, new SavePictureAction());
        ACTIONS.put(Actions.VIBRATE, new VibrateAction());
        ACTIONS.put(Actions.PLAY_ALARM, new PlayAlarmAction());
        //ACTIONS.put(Actions.SEND_PICTURE_BLUETOOTH, new SendPictureBluetoothAction());
        ACTIONS.put(Actions.UPLOAD_PICTURE_GOOGLEDRIVE, new UploadPictureGoogledriveAction());

        mGoogleApiClient = (new GoogleApiClient.Builder(MonitoringActivity.this))
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .enableAutoManage(MonitoringActivity.this, null)
                .useDefaultAccount()
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Log.i(TAG, "Google API connected.");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Log.e(TAG, "Google API connection failed: " + connectionResult.getErrorMessage());
                    }
                })
                .build();

        Log.d(TAG, "Audio buffer size: " + audioRecorderBufferSize);
        mAudioRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT,
                audioRecorderBufferSize);

        mScheduledPool = Executors.newScheduledThreadPool(1);

        setContentView(R.layout.activity_monitoring);

        mBtConfigure = (Button) findViewById(R.id.bt_configure);
        mBtStart = (Button) findViewById(R.id.bt_start);
        mBtStop = (Button) findViewById(R.id.bt_stop);

        checkPermissions();

        if (savedInstanceState != null) {
            mIsMonitoringStarted = savedInstanceState.getBoolean("mIsMonitoringStarted");
        }
        updateButtons();

        mBtConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MonitoringActivity.this, ConfigureActivity.class);
                startActivity(intent);
            }
        });

        mBtStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMonitoring();
            }
        });

        mBtStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMonitoring();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("mIsMonitoringStarted", mIsMonitoringStarted);
        super.onSaveInstanceState(outState);
    }

    @Nullable
    private Detector<?> loadDetectors(Map<String, ?> cameraTriggers) {
        Detector<?> detector = null;
        MultiDetector.Builder detectorBuilder = new MultiDetector.Builder();
        int count = 0;

        for (Map.Entry<String, ?> entry : cameraTriggers.entrySet()) {
            String trigger = entry.getKey();
            detector = CAMERA_TRIGGER_DETECTORS.get(trigger);

            if (detector == null) {
                Log.w(TAG, "Non-existing camera trigger in preferences: " + trigger);
                continue;
            }

            detectorBuilder.add(detector);
            count++;

            Collection<String> actions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                actions = (Set<String>) entry.getValue();
            } else {
                actions = Serialization.deserializeString((String) entry.getValue());
            }

            List<Action> actionList = new LinkedList<>();

            for (String actionString : actions) {
                Log.d(TAG, "Added " + actionString + " to " + entry.getKey());
                actionList.add(ACTIONS.get(actionString));
            }

            detector.setProcessor(new TriggerProcessor(actionList, 5000, TriggerProcessor.NON_EMPTY));

        }

        if (count == 0) return null;

        if (count > 1) detector = detectorBuilder.build();

        return detector;
    }


    @Override
    protected void onStart() {
        super.onStart();

        //------------------init-camera------------------

        SharedPreferences prefs = getSharedPreferences(CAMERA_TRIGGERED_PREFS, Context.MODE_PRIVATE);
        Map<String, Set<String>> prefMap = (Map<String, Set<String>>) prefs.getAll();

        Log.d(TAG, "Loaded preferences");

        Detector<?> detector = loadDetectors(prefMap);

        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }

        if (detector != null) {
            mCameraSource = new CameraSource.Builder(getApplicationContext(), detector)
                    .setRequestedPreviewSize(640, 480)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedFps(30.0f)
                    .build();
        }

        //------------------init-sound------------------

        prefs = getSharedPreferences(SOUND_TRIGGERED_PREFS, Context.MODE_PRIVATE);
        prefMap = (Map<String, Set<String>>) prefs.getAll();


        List<SoundTrigger> soundTriggers = loadSoundTriggers(prefMap);

        mSoundSource = new SoundSource(soundTriggers);


        //------------------init-other------------------

        mGoogleApiClient.connect();

        if (mIsMonitoringStarted) {
            startMonitoring();
        }
    }

    private List<SoundTrigger> loadSoundTriggers(Map<String, Set<String>> soundTriggers) {
        List<SoundTrigger> retList = new ArrayList<>(soundTriggers.size());

        for (Map.Entry<String, ?> entry : soundTriggers.entrySet()) {
            String triggerKey = entry.getKey();
            SoundTrigger trigger = SOUND_TRIGGERS.get(triggerKey);

            if (trigger == null) {
                Log.w(TAG, "Non-existing sound trigger in preferences: " + triggerKey);
                continue;
            }

            Collection<String> actions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                actions = (Set<String>) entry.getValue();
            } else {
                actions = Serialization.deserializeString((String) entry.getValue());
            }

            List<Action> actionList = new LinkedList<>();

            for (String actionString : actions) {
                Log.d(TAG, "Added " + actionString + " to " + entry.getKey());
                actionList.add(ACTIONS.get(actionString));
            }

            trigger.setActions(actionList);
            retList.add(trigger);
        };

        return retList;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();

    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            stopCamera();
            mCameraSource.release();
            mCameraSource = null;
        }

        if (mScheduledPool != null) {
            mScheduledPool.shutdown();
        }
    }

    private void checkPermissions() {
        for (int i = 0; i < PERMISSIONS.length; i++) {
            if (ActivityCompat.checkSelfPermission(this, PERMISSIONS[i]) !=
                    PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{PERMISSIONS[i]}, i);
            }
        }
    }

    private void startCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                closeApp();
            }
            mCameraSource.start();
            Log.i(TAG, "Started camera.");

        } catch (IOException e) {
            Log.e(TAG, "Unable to start camera source.", e);
            closeApp();
        }
    }

    private void stopCamera() {
        mCameraSource.stop();
        Log.i(TAG, "Stopped camera.");
    }

    private void startMonitoring() {
        mIsMonitoringStarted = true;
        updateButtons();

        mAudioRecorder.startRecording();

        mScheduledPoolFuture = mScheduledPool.scheduleAtFixedRate(
                mSoundSource,
                1000,
                1000 * audioRecorderBufferSize / 44100,
                TimeUnit.MILLISECONDS);

        if (mCameraSource != null) {
            startCamera();
        }
    }

    private void stopMonitoring() {
        mIsMonitoringStarted = false;
        updateButtons();

        mAudioRecorder.stop();
        mScheduledPoolFuture.cancel(true);

        if (mCameraSource != null) {
            stopCamera();
        }
    }

    private void closeApp() {
        Log.d(TAG, "Closing app...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        } else {
            finish();
        }
    }

    private void updateButtons() {
        mBtConfigure.setEnabled(!mIsMonitoringStarted);
        mBtStart.setEnabled(!mIsMonitoringStarted);
        mBtStop.setEnabled(mIsMonitoringStarted);
    }

    private class SavePictureAction implements Action {
        @Override
        public void performAction() {
            mCameraSource.takePicture(null, new CameraSource.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] bytes) {
                    new SavePictureTask().execute(bytes);
                }
            });
        }
    }

    private class VibrateAction implements Action {
        Vibrator vibrator = (Vibrator) getApplicationContext()
                .getSystemService(Context.VIBRATOR_SERVICE);

        @Override
        public void performAction() {
            vibrator.vibrate(500);
        }
    }

    private class PlayAlarmAction implements Action {
        @Override
        public void performAction() {
            MediaPlayer mp = MediaPlayer.create(MonitoringActivity.this, R.raw.alarm);
            mp.start();

            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    Log.d(TAG, "MediaPlayer released");
                }
            });
        }
    }

    private class SendPictureBluetoothAction implements Action {

        @Override
        public void performAction() {
            return;
            //TODO
        }
    }

    private class UploadPictureGoogledriveAction implements Action {

        @Override
        public void performAction() {
            mCameraSource.takePicture(null, new CameraSource.PictureCallback() {
                @Override
                public void onPictureTaken(final byte[] bytes) {
                    Drive.DriveApi
                            .newDriveContents(mGoogleApiClient)
                            .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                                @Override
                                public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                                    new UploadPictureGoogledriveTask().execute(bytes);

                                }
                            });

                    Log.v(TAG, "Drive contents callback set");

                }
            });
        }
    }

    private class SendPictureBluetoothTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... params) {
            //TODO
            return null;
        }
    }

    private class UploadPictureGoogledriveTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(final byte[]... jpeg) {
            Drive.DriveApi
                    .newDriveContents(mGoogleApiClient)
                    .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                        @Override
                        public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                            MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                    .setMimeType("image/jpeg")
                                    .setTitle(getPictureTitle())
                                    .build();

                            Log.v(TAG, "Metadata set");
                            Log.d(TAG, "UI thread: " + (Looper.myLooper() == Looper.getMainLooper()));

                            DriveContents driveContents = driveContentsResult.getDriveContents();
                            try {
                                driveContents.getOutputStream().write(jpeg[0]);
                                Drive.DriveApi.getRootFolder(mGoogleApiClient).createFile(
                                        mGoogleApiClient,
                                        metadataChangeSet,
                                        driveContents
                                );
                                Log.i(TAG, "Added file to drive");
                            } catch (IOException e) {
                                Log.e(TAG, "Could not add file to drive", e);
                            }

                        }
                    });

            Log.v(TAG, "Drive contents callback set");
            return null;
        }
    }


    private class SavePictureTask extends AsyncTask<byte[], Void, Void> {
        @Override
        protected Void doInBackground(byte[]... jpeg) {
            String pictureName = getPictureTitle();

            File picture = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), pictureName + ".jpg");

            Log.d(TAG, "File object created");

            if (picture.exists()) {
                picture.delete();
            }

            try {
                FileOutputStream fos = new FileOutputStream(picture.getPath());

                fos.write(jpeg[0]);
                fos.close();
                Log.d(TAG, "Photo taken!");
            } catch (java.io.IOException e) {
                Log.e(TAG, "Exception in photoCallback", e);
            }

            return (null);
        }
    }

    private static String getPictureTitle() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        return dateFormat.format(new Date());
    }

    private class NoiseTrigger extends SoundTrigger {
        private static final double REFERENCE = 0.00002;

        private int cooldown;
        private long lastDetectionTime;
        private int noiseLevel;

        public NoiseTrigger(int cooldown, int noiseLevel) {
            this.cooldown = cooldown;
            this.noiseLevel = noiseLevel;
        }

        @Override
        public void receiveAudioData(short[] data, int size) {
            long currTime = System.currentTimeMillis();

            long sum = 0;
            for (short s : data) {
                sum += Math.abs(s);
            }

            double avg = (double) sum / size;
            double pressure = avg/51805.5336;
            double db = (20 * Math.log10(pressure/REFERENCE));

            if (db > noiseLevel && currTime > lastDetectionTime + cooldown) {
                lastDetectionTime = currTime;
                triggerActions();

                //za debugiranje:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MonitoringActivity.this, "Noise", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private class SoundSource implements Runnable {
        private short[] audioBuffer;
        private List<AudioConsumer> audioConsumers;

        public SoundSource(List<? extends AudioConsumer> audioConsumers) {
            audioBuffer = new short[audioRecorderBufferSize];
            this.audioConsumers = new ArrayList<>(audioConsumers);
        }

        @Override
        public void run() {
            mAudioRecorder.read(audioBuffer, 0, audioRecorderBufferSize);
            for (AudioConsumer audioConsumer : audioConsumers) {
                audioConsumer.receiveAudioData(audioBuffer, audioRecorderBufferSize);
            }
        }
    }
}

