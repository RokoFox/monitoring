package monitoring.seminar.fer.monitoring;

import java.util.List;

/**
 * Created by roko on 4/29/17.
 */

public interface AudioConsumer {
    void receiveAudioData(short data[], int size);
}
