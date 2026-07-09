package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Writes intermediate CV processing images (original, edges, contour overlay) to a configured debug directory.
 * No-op when the directory is not configured.
 */
@Component
public class CvImageDebugger {

    private static final Logger log = LoggerFactory.getLogger(CvImageDebugger.class);

    @Value("${calibration.debug.dump-dir:}")
    private String debugDumpDir;

    public void dump(Mat image, Mat edges, List<MatOfPoint> contours) {
        if (debugDumpDir == null || debugDumpDir.isBlank()) {
            return;
        }
        try {
            File dir = new File(debugDumpDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            long ts = System.currentTimeMillis();
            Imgcodecs.imwrite(debugDumpDir + "/" + ts + "_original.png", image);
            Imgcodecs.imwrite(debugDumpDir + "/" + ts + "_edges.png", edges);

            Mat overlay = image.clone();
            Imgproc.drawContours(overlay, contours, -1, new Scalar(0, 0, 255), 2);
            Imgcodecs.imwrite(debugDumpDir + "/" + ts + "_contours.png", overlay);
            overlay.release();

            log.info("[CvDetector] Debug images dumped to {} (ts={})", debugDumpDir, ts);
        } catch (Exception e) {
            log.warn("[CvDetector] Debug dump failed: {}", e.getMessage());
        }
    }
}