package monitoring.seminar.fer.monitoring.detectors.motion;

import android.graphics.Bitmap;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

/**
 * Created by roko on 4/2/17.
 */

public class MotionDetector extends Detector<Boolean> {
    private static final int TOLERANCE = Integer.MAX_VALUE;
    private int[] previousPixels;

    @Override
    public SparseArray<Boolean> detect(Frame frame) {
        Bitmap bitmap = frame.getBitmap();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int subWidth = width / 8;
        int subHeight = height / 8;

        SparseArray<Boolean> ret = new SparseArray<>();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {

                int mad = 0;

                for (int k = 0; k < subHeight; k++) {
                    for (int l = 0; l < subWidth; l++) {
                        int index = (i * subHeight + k) * width + (j * subWidth + l);
                        int red, green, blue;

                        red = Math.abs(((previousPixels[index] >> 16) & 0xff) - ((pixels[index] >> 16) & 0xff));
                        green = Math.abs(((previousPixels[index] >> 8) & 0xff) - ((pixels[index] >> 8) & 0xff));
                        blue = Math.abs((previousPixels[index] & 0xff) - (pixels[index] & 0xff));

                        mad += red + green + blue;
                    }
                }

                if (mad / (subHeight * subWidth) > TOLERANCE) {
                    ret.append(i * 8 + j, true);
                }
            }
        }

        previousPixels = pixels;
        return ret;
    }
}
