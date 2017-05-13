package monitoring.seminar.fer.monitoring;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.SimpleAdapter;
import android.widget.Spinner;

import java.util.HashSet;
import java.util.Set;

public class ConfigureActivity extends AppCompatActivity {
    private static final String TAG = "ConfigureActivity";

    private Spinner spFaceDetected1;
    private Spinner spFaceDetected2;

    private Spinner spNoiseDetected1;
    private Spinner spNoiseDetected2;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure);

        spFaceDetected1 = (Spinner) findViewById(R.id.spFaceDetected1);
        spFaceDetected2 = (Spinner) findViewById(R.id.spFaceDetected2);

        spNoiseDetected1 = (Spinner) findViewById(R.id.spNoiseDetected1);
        spNoiseDetected2 = (Spinner) findViewById(R.id.spNoiseDetected2);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Actions.ALL);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);

        spFaceDetected1.setAdapter(spinnerAdapter);
        spFaceDetected2.setAdapter(spinnerAdapter);

        spNoiseDetected1.setAdapter(spinnerAdapter);
        spNoiseDetected2.setAdapter(spinnerAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        saveCameraPrefs();
        saveSoundPrefs();
    }

    private void saveCameraPrefs() {
        SharedPreferences.Editor prefs = getSharedPreferences(MonitoringActivity.CAMERA_TRIGGERED_PREFS,
                Context.MODE_PRIVATE).edit();

        Set<String> faceDetectedActions = new HashSet<>();

        String selectedItem = (String) spFaceDetected1.getSelectedItem();
        if (selectedItem != null) {
            faceDetectedActions.add(selectedItem);
        }

        selectedItem = (String) spFaceDetected2.getSelectedItem();
        if (selectedItem != null) {
            faceDetectedActions.add(selectedItem);
        }

        Log.d(TAG, "" + faceDetectedActions + " added to preferences");

        if (! faceDetectedActions.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                prefs.putStringSet(ActionTriggers.FACE_DETECTED, faceDetectedActions);
            } else {
                prefs.putString(ActionTriggers.FACE_DETECTED,
                        Serialization.serializeStrings(faceDetectedActions));
            }
        }

        prefs.apply();
    }

    private void saveSoundPrefs() {
        SharedPreferences.Editor prefs = getSharedPreferences(MonitoringActivity.SOUND_TRIGGERED_PREFS,
                Context.MODE_PRIVATE).edit();

        Set<String> noiseDetectedActions = new HashSet<>();

        String selectedItem = (String) spNoiseDetected1.getSelectedItem();
        if (selectedItem != null) {
            noiseDetectedActions.add(selectedItem);
        }

        selectedItem = (String) spNoiseDetected2.getSelectedItem();
        if (selectedItem != null) {
            noiseDetectedActions.add(selectedItem);
        }

        Log.d(TAG, "" + noiseDetectedActions + " added to preferences");

        if (! noiseDetectedActions.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                prefs.putStringSet(ActionTriggers.NOISE_DETECTED, noiseDetectedActions);
            } else {
                prefs.putString(ActionTriggers.NOISE_DETECTED,
                        Serialization.serializeStrings(noiseDetectedActions));
            }
        }

        prefs.apply();
    }


}
