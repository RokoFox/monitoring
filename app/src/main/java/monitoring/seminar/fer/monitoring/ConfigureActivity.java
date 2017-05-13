package monitoring.seminar.fer.monitoring;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ConfigureActivity extends AppCompatActivity {
    private static final String TAG = "ConfigureActivity";
    private static final String DO_NOTHING = "DO_NOTHING";

    private Set<Spinner> mFaceSpinners = new HashSet<>();
    private Set<Spinner> mNoiseSpinners = new HashSet<>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure);

        mFaceSpinners.add((Spinner) findViewById(R.id.spFaceDetected1));
        mFaceSpinners.add((Spinner) findViewById(R.id.spFaceDetected2));

        mNoiseSpinners.add((Spinner) findViewById(R.id.spNoiseDetected1));
        mNoiseSpinners.add((Spinner) findViewById(R.id.spNoiseDetected2));

        List<String> allActions = new LinkedList<>(Arrays.asList(Actions.ALL));
        allActions.add(0, DO_NOTHING);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                allActions
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);

        for (Spinner spinner : mFaceSpinners) {
            spinner.setAdapter(spinnerAdapter);
        }

        for (Spinner spinner : mNoiseSpinners) {
            spinner.setAdapter(spinnerAdapter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        savePrefs(mFaceSpinners, MonitoringActivity.CAMERA_TRIGGERED_PREFS, ActionTriggers.FACE_DETECTED);
        savePrefs(mNoiseSpinners, MonitoringActivity.SOUND_TRIGGERED_PREFS, ActionTriggers.NOISE_DETECTED);
    }

    private void savePrefs(Set<Spinner> spinners, String fileName, String triggerName) {
        SharedPreferences.Editor prefs = getSharedPreferences(fileName,
                Context.MODE_PRIVATE).edit();

        Set<String> actions = new HashSet<>();

        String selectedItem;
        for (Spinner spinner : spinners) {
            selectedItem = (String) spinner.getSelectedItem();

            if (selectedItem != null && ! selectedItem.equals(DO_NOTHING)) {
                actions.add(selectedItem);
            }
        }

        Log.d(TAG, "" + actions + " added to preferences");

        if (! actions.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                prefs.putStringSet(triggerName, actions);
            } else {
                prefs.putString(triggerName,
                        Serialization.serializeStrings(actions));
            }
        } else {
            prefs.remove(triggerName);
        }

        prefs.apply();
    }

//    private void saveSoundPrefs() {
//        SharedPreferences.Editor prefs = getSharedPreferences(MonitoringActivity.SOUND_TRIGGERED_PREFS,
//                Context.MODE_PRIVATE).edit();
//
//        Set<String> noiseDetectedActions = new HashSet<>();
//
//        String selectedItem = (String) spNoiseDetected1.getSelectedItem();
//        if (selectedItem != null) {
//            noiseDetectedActions.add(selectedItem);
//        }
//
//        selectedItem = (String) spNoiseDetected2.getSelectedItem();
//        if (selectedItem != null) {
//            noiseDetectedActions.add(selectedItem);
//        }
//
//        Log.d(TAG, "" + noiseDetectedActions + " added to preferences");
//
//        if (! noiseDetectedActions.isEmpty()) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//                prefs.putStringSet(ActionTriggers.NOISE_DETECTED, noiseDetectedActions);
//            } else {
//                prefs.putString(ActionTriggers.NOISE_DETECTED,
//                        Serialization.serializeStrings(noiseDetectedActions));
//            }
//        }
//
//        prefs.apply();
}
