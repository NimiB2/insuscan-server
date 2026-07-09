package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirms a reference-object candidate within a specific image region (typically a Gemini bbox).
 * Clusters edge fragments inside the cropped region and picks the cluster whose aspect ratio best matches the expected object.
 * Used for cross-checking the vision model's detection against a physical CV measurement.
 */
@Component
public class PointCloudRegionDetector {

    private static final Logger log = LoggerFactory.getLogger(PointCloudRegionDetector.class);

    private static final double BLUR_KERNEL_SIZE = 5.0;
    private static final double BLUR_SIGMA = 0.0;
    private static final double CANNY_THRESHOLD_LOW = 50.0;
    private static final double CANNY_THRESHOLD_HIGH = 150.0;
    private static final int MIN_CLOUD_POINTS = 50;

    /** Maximum confidence assigned to a region cluster measurement, scaled down by aspect deviation. */
    private static final float MAX_CONFIDENCE_FROM_ASPECT = 0.9f;

    @Value("${calibration.strict.aspect-tolerance:0.5}")
    private double strictAspectTolerance;

    @Value("${calibration.cv.region-margin-ratio:0.5}")
    private double regionMarginRatio;

    @Value("${calibration.cv.cluster-merge-ratio:0.05}")
    private double clusterMergeRatio;

    private final CvImageDebugger debugger;

    public PointCloudRegionDetector(CvImageDebugger debugger) {
        this.debugger = debugger;
    }

    public CvDetectionResult detect(Mat image, float expectedLengthCm, float expectedWidthCm, float[] regionPx) {
        if (regionPx == null || regionPx.length < 4 || regionPx[2] <= 0 || regionPx[3] <= 0) {
            return CvDetectionResult.notFound("Invalid region for focused detection");
        }

        Mat gray = new Mat();
        Mat edges = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();

        try {
            double marginX = regionPx[2] * regionMarginRatio;
            double marginY = regionPx[3] * regionMarginRatio;

            int x = (int) Math.max(0, regionPx[0] - marginX);
            int y = (int) Math.max(0, regionPx[1] - marginY);
            int w = (int) Math.min(image.cols() - x, regionPx[2] + 2 * marginX);
            int h = (int) Math.min(image.rows() - y, regionPx[3] + 2 * marginY);

            if (w <= 0 || h <= 0) {
                return CvDetectionResult.notFound("Region outside image bounds");
            }

            Mat cropped = new Mat(image, new Rect(x, y, w, h));

            Imgproc.cvtColor(cropped, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(BLUR_KERNEL_SIZE, BLUR_KERNEL_SIZE), BLUR_SIGMA);
            Imgproc.Canny(gray, edges, CANNY_THRESHOLD_LOW, CANNY_THRESHOLD_HIGH);
            Imgproc.findContours(edges, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            debugger.dump(cropped, edges, contours);
            cropped.release();

            List<List<Point>> fragments = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect contourBox = Imgproc.boundingRect(contour);
                boolean touchesBorder = contourBox.x <= 1 || contourBox.y <= 1
                        || contourBox.x + contourBox.width >= w - 1
                        || contourBox.y + contourBox.height >= h - 1;
                if (!touchesBorder) {
                    fragments.add(contour.toList());
                }
            }

            List<List<Point>> clusters = clusterFragments(fragments, w, h);
            RotatedRect cloudRect = selectBestCluster(clusters, expectedLengthCm / expectedWidthCm);

            if (cloudRect == null) {
                return CvDetectionResult.notFound("No pen-like cluster found in region");
            }

            double length = Math.max(cloudRect.size.width, cloudRect.size.height);
            double thickness = Math.min(cloudRect.size.width, cloudRect.size.height);

            if (thickness <= 0) {
                return CvDetectionResult.notFound("Degenerate point cloud");
            }

            double aspectRatio = length / thickness;
            double expectedAspect = expectedLengthCm / expectedWidthCm;
            double aspectDeviation = Math.abs(aspectRatio - expectedAspect) / expectedAspect;

            if (aspectDeviation > strictAspectTolerance) {
                log.info("[CvDetector][Region] Point cloud rejected: aspect={} expected={} deviation={}%",
                        String.format("%.2f", aspectRatio), String.format("%.2f", expectedAspect),
                        String.format("%.0f", aspectDeviation * 100));
                return CvDetectionResult.notFound("Point cloud aspect mismatch");
            }

            float confidence = (float) (MAX_CONFIDENCE_FROM_ASPECT * (1.0 - aspectDeviation / strictAspectTolerance));
            float pixelToCmRatio = (float) (expectedLengthCm / length);

            log.info("[CvDetector][Region] Point cloud measured: length={}px aspect={} deviation={}% confidence={} ratio={}",
                    (int) length, String.format("%.2f", aspectRatio),
                    String.format("%.0f", aspectDeviation * 100), String.format("%.2f", confidence),
                    String.format("%.5f", pixelToCmRatio));

            Rect adjustedBox = new Rect((int) (cloudRect.boundingRect().x + x),
                    (int) (cloudRect.boundingRect().y + y),
                    cloudRect.boundingRect().width, cloudRect.boundingRect().height);
            Point adjustedCenter = new Point(cloudRect.center.x + x, cloudRect.center.y + y);

            return CvDetectionResult.found(adjustedBox, adjustedCenter, cloudRect.angle,
                    length, pixelToCmRatio, confidence);

        } finally {
            gray.release();
            edges.release();
            hierarchy.release();
        }
    }

    private List<List<Point>> clusterFragments(List<List<Point>> fragments, int w, int h) {
        double mergeDistance = Math.max(w, h) * clusterMergeRatio;
        List<List<Point>> clusters = new ArrayList<>();
        List<Point[]> clusterBounds = new ArrayList<>();

        for (List<Point> fragment : fragments) {
            if (fragment.isEmpty()) {
                continue;
            }
            Rect box = Imgproc.boundingRect(new MatOfPoint(fragment.toArray(new Point[0])));
            Point center = new Point(box.x + box.width / 2.0, box.y + box.height / 2.0);

            int target = -1;
            for (int i = 0; i < clusterBounds.size(); i++) {
                Point existing = clusterBounds.get(i)[0];
                double dist = Math.hypot(center.x - existing.x, center.y - existing.y);
                if (dist <= mergeDistance) {
                    target = i;
                    break;
                }
            }

            if (target >= 0) {
                clusters.get(target).addAll(fragment);
                List<Point> merged = clusters.get(target);
                Rect mergedBox = Imgproc.boundingRect(new MatOfPoint(merged.toArray(new Point[0])));
                clusterBounds.get(target)[0] = new Point(
                        mergedBox.x + mergedBox.width / 2.0,
                        mergedBox.y + mergedBox.height / 2.0);
            } else {
                List<Point> newCluster = new ArrayList<>(fragment);
                clusters.add(newCluster);
                clusterBounds.add(new Point[] { center });
            }
        }

        return clusters;
    }

    private RotatedRect selectBestCluster(List<List<Point>> clusters, double expectedAspect) {
        RotatedRect best = null;
        double bestDeviation = Double.MAX_VALUE;

        for (List<Point> cluster : clusters) {
            if (cluster.size() < MIN_CLOUD_POINTS) {
                continue;
            }
            MatOfPoint2f cloud2f = new MatOfPoint2f(cluster.toArray(new Point[0]));
            RotatedRect rect = Imgproc.minAreaRect(cloud2f);
            cloud2f.release();

            double length = Math.max(rect.size.width, rect.size.height);
            double thickness = Math.min(rect.size.width, rect.size.height);
            if (thickness <= 0) {
                continue;
            }

            double aspect = length / thickness;
            double deviation = Math.abs(aspect - expectedAspect) / expectedAspect;
            if (deviation < bestDeviation) {
                bestDeviation = deviation;
                best = rect;
            }
        }

        return best;
    }
}