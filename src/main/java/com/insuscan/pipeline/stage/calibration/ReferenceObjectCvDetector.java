package com.insuscan.pipeline.stage.calibration;

import jakarta.annotation.PostConstruct;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Detects the reference object (insulin pen or credit card) in a meal photo using OpenCV.
 * Full-image detection runs a Canny + contour pipeline and delegates candidate scoring to a per-mode {@link ReferenceObjectStrategy}.
 * Region-focused verification is delegated to {@link PointCloudRegionDetector}.
 */
@Component
public class ReferenceObjectCvDetector {

    private static final Logger log = LoggerFactory.getLogger(ReferenceObjectCvDetector.class);

    static {
        OpenCV.loadLocally();
    }

    private static final double MIN_CONTOUR_AREA = 1000.0;
    private static final double MAX_CONTOUR_AREA = 500000.0;
    private static final double BLUR_KERNEL_SIZE = 5.0;
    private static final double BLUR_SIGMA = 0.0;
    private static final double CANNY_THRESHOLD_LOW = 50.0;
    private static final double CANNY_THRESHOLD_HIGH = 150.0;
    private static final double REF_RESOLUTION_PIXELS = 2_000_000.0;

    @Value("${calibration.strict.aspect-tolerance:0.5}")
    private double strictAspectTolerance;

    @Value("${calibration.cv.close-kernel-px:5}")
    private int closeKernelPx;

    private final PointCloudRegionDetector regionDetector;
    private final CvImageDebugger debugger;

    private Map<DetectionMode, ReferenceObjectStrategy> strategies;

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

    public ReferenceObjectCvDetector(PointCloudRegionDetector regionDetector, CvImageDebugger debugger) {
        this.regionDetector = regionDetector;
        this.debugger = debugger;
    }

    @PostConstruct
    private void initStrategies() {
        strategies = Map.of(
                DetectionMode.STRICT, new StrictReferenceStrategy(strictAspectTolerance),
                DetectionMode.CARD, new CardReferenceStrategy());
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

        Mat image = decodeBase64ToMat(base64Image);
        if (image == null || image.empty()) {
            return CvDetectionResult.notFound("Failed to decode image");
        }
        try {
            return regionDetector.detect(image, expectedLengthCm, expectedWidthCm, regionPx);
        } finally {
            image.release();
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
            debugger.dump(image, edges, contours);

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
}