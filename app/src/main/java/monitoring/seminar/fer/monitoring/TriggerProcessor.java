package monitoring.seminar.fer.monitoring;

import android.support.annotation.NonNull;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Detector.Processor;

import java.util.List;

/**
 * Created by roko on 4/1/17.
 */

public class TriggerProcessor<T> extends Trigger implements Processor<T> {
    public static final int NON_EMPTY = 1;
    public static final int EMPTY = 0;

    private final int condition;
    private final int cooldown;
    private long lastDetectionTime;
    private boolean lastDetection;

    public TriggerProcessor(@NonNull List<? extends Action> actions, int cooldown, int condition) {
        setActions(actions);

        if (cooldown < 0) {
            throw new IllegalArgumentException(
                    "Cooldown must be a non negative integer, given: " + cooldown);
        }

        this.condition = condition;
        this.cooldown = cooldown;
    }

    @Override
    public void receiveDetections(Detector.Detections<T> detections) {
        long currTime = System.currentTimeMillis();

        if ((detections.getDetectedItems().size() > 0 && condition == NON_EMPTY)
                || (detections.getDetectedItems().size() == 0 && condition == EMPTY)) {
            if (lastDetection && currTime < lastDetectionTime + cooldown) return;
            lastDetectionTime = currTime;
            lastDetection = true;

            triggerActions();
        }
    }

    @Override
    public void release() {
    }
}
