package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.opencv.core.Scalar;
import org.springframework.beans.factory.annotation.Value;
import java.io.File;
import jakarta.annotation.PostConstruct;

@Component
public class ReferenceObjectCvDetector {

    private static final Logger log = LoggerFactory.getLogger(ReferenceObjectCvDetector.class);

    private static final double MIN_CONTOUR_AREA = 1000.0;
    private static final double MAX_CONTOUR_AREA = 500000.0;
    private static final double BLUR_KERNEL_SIZE = 5.0;
    private static final double BLUR_SIGMA = 0.0;
    private static final double CANNY_THRESHOLD_LOW = 50.0;
    private static final double CANNY_THRESHOLD_HIGH = 150.0;
    private static final double REF_RESOLUTION_PIXELS = 2_000_000.0;
    private static final int MIN_CLOUD_POINTS = 50;

    
    @Value("${calibration.strict.aspect-tolerance:0.5}")
    private double strictAspectTolerance;
    
    @Value("${calibration.cv.close-kernel-px:5}")
    private int closeKernelPx;

    @Value("${calibration.cv.region-margin-ratio:0.5}")
    private double regionMarginRatio;
    
    @Value("${calibration.debug.dump-dir:}")
    private String debugDumpDir;

    public enum DetectionMode {
        STRICT,
        CARD;

        public static DetectionMode fromReferenceType(String referenceType) {
            if (referenceType != null && referenceType.toUpperCase().contains("CARD")) {
                return CARD;
            }
            return STRICT;
        }
    }

    private Map<DetectionMode, ReferenceObjectStrategy> strategies;

    @PostConstruct
    private void initStrategies() {
        strategies = Map.of(
                DetectionMode.STRICT, new StrictReferenceStrategy(strictAspectTolerance),
                DetectionMode.CARD, new CardReferenceStrategy());
    }
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public CvDetectionResult detect(String base64Image, DetectionMode mode,
            float expectedLengthCm, float expectedWidthCm) {

        Mat image = decodeBase64ToMat(base64Image);
        if (image == null || image.empty()) {
            return CvDetectionResult.notFound("Failed to decode image");
        }
        try {
            return runDetection(image, mode, expectedLengthCm, expectedWidthCm);
        } finally {
            image.release();
        }
    }

public CvDetectionResult detectInRegion(String base64Image, DetectionMode mode,
            float expectedLengthCm, float expectedWidthCm, float[] regionPx) {

        if (regionPx == null || regionPx.length < 4 || regionPx[2] <= 0 || regionPx[3] <= 0) {
            return CvDetectionResult.notFound("Invalid region for focused detection");
        }

        Mat image = decodeBase64ToMat(base64Image);
        if (image == null || image.empty()) {
            return CvDetectionResult.notFound("Failed to decode image");
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

            dumpDebugImages(cropped, edges, contours);
            cropped.release();

            List<Point> cloudPoints = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect contourBox = Imgproc.boundingRect(contour);
                boolean touchesBorder = contourBox.x <= 1 || contourBox.y <= 1
                        || contourBox.x + contourBox.width >= w - 1
                        || contourBox.y + contourBox.height >= h - 1;
                if (!touchesBorder) {
                    cloudPoints.addAll(contour.toList());
                }
            }

            if (cloudPoints.size() < MIN_CLOUD_POINTS) {
                return CvDetectionResult.notFound("Too few edge points in region ("
                        + cloudPoints.size() + ")");
            }

            MatOfPoint2f cloud2f = new MatOfPoint2f(cloudPoints.toArray(new Point[0]));
            RotatedRect cloudRect = Imgproc.minAreaRect(cloud2f);
            cloud2f.release();

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

            float confidence = (float) (0.9 * (1.0 - aspectDeviation / strictAspectTolerance));
            float pixelToCmRatio = (float) (expectedLengthCm / length);

            log.info("[CvDetector][Region] Point cloud measured: points={} length={}px aspect={} deviation={}% confidence={} ratio={}",
                    cloudPoints.size(), (int) length, String.format("%.2f", aspectRatio),
                    String.format("%.0f", aspectDeviation * 100), String.format("%.2f", confidence),
                    String.format("%.5f", pixelToCmRatio));

            Rect adjustedBox = new Rect((int) (cloudRect.boundingRect().x + x),
                    (int) (cloudRect.boundingRect().y + y),
                    cloudRect.boundingRect().width, cloudRect.boundingRect().height);
            Point adjustedCenter = new Point(cloudRect.center.x + x, cloudRect.center.y + y);

            return CvDetectionResult.found(adjustedBox, adjustedCenter, cloudRect.angle,
                    length, pixelToCmRatio, confidence);

        } finally {
            image.release();
            gray.release();
            edges.release();
            hierarchy.release();
        }
    }

    private CvDetectionResult runDetection(Mat image, DetectionMode mode,
            float expectedLengthCm, float expectedWidthCm) {

        Mat gray = new Mat();
        Mat edges = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();

        try {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(BLUR_KERNEL_SIZE, BLUR_KERNEL_SIZE), BLUR_SIGMA);
            Imgproc.Canny(gray, edges, CANNY_THRESHOLD_LOW, CANNY_THRESHOLD_HIGH);

            Mat closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                    new Size(closeKernelPx, closeKernelPx));
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, closeKernel);
            closeKernel.release();

            Imgproc.findContours(edges, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            dumpDebugImages(image, edges, contours);

            return findBestReferenceObject(contours, image.cols(), image.rows(),
                    mode, expectedLengthCm, expectedWidthCm);

        } finally {
            gray.release();
            edges.release();
            hierarchy.release();
        }
    }

    private CvDetectionResult findBestReferenceObject(List<MatOfPoint> contours,
            int imgWidthPx, int imgHeightPx, DetectionMode mode,
            float expectedLengthCm, float expectedWidthCm) {

        ReferenceObjectStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            return CvDetectionResult.notFound("Unsupported detection mode: " + mode);
        }

        double imgWidth = imgWidthPx;
        double totalPixels = (double) imgWidthPx * imgHeightPx;
        double scaleFactor = totalPixels / REF_RESOLUTION_PIXELS;
        double minArea = MIN_CONTOUR_AREA * scaleFactor;
        double maxArea = MAX_CONTOUR_AREA * scaleFactor;

        List<CvDetectionResult> candidates = new ArrayList<>();
        int tooSmall = 0;
        int tooLarge = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < minArea) {
                tooSmall++;
                continue;
            }
            if (area > maxArea) {
                tooLarge++;
                continue;
            }

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            RotatedRect rotatedRect = Imgproc.minAreaRect(contour2f);
            contour2f.release();

            CvDetectionResult candidate = strategy.evaluateContour(
                    contour, rotatedRect, area, imgWidth, expectedLengthCm, expectedWidthCm);

            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        log.info("[CvDetector] mode={} contours={} tooSmall={} tooLarge={} candidates={}",
                mode, contours.size(), tooSmall, tooLarge, candidates.size());

        return candidates.stream()
                .max(Comparator.comparingDouble(CvDetectionResult::getConfidence))
                .orElse(CvDetectionResult.notFound("No valid reference object found"));
    }

    private Mat decodeBase64ToMat(String base64Image) {
        try {
            String cleaned = base64Image.contains(",")
                    ? base64Image.substring(base64Image.indexOf(',') + 1)
                    : base64Image;
            byte[] bytes = Base64.getDecoder().decode(cleaned);
            MatOfByte buffer = new MatOfByte(bytes);
            Mat image = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
            buffer.release();
            return image;
        } catch (Exception e) {
            log.warn("[CvDetector] Base64 decode failed: {}", e.getMessage());
            return null;
        }
    }
    
    private void dumpDebugImages(Mat image, Mat edges, List<MatOfPoint> contours) {
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