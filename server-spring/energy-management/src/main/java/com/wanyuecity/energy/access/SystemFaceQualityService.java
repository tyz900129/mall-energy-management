package com.wanyuecity.energy.access;

import jakarta.annotation.PostConstruct;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Platform-local face and image quality validation; no access device is contacted. */
@Service
public class SystemFaceQualityService {
    private static final String MODEL = "models/face_detection_yunet_2023mar.onnx";
    private static final int MAX_DETECTION_EDGE = 1000;
    private static final double MIN_SHARPNESS = 18d;
    private final Object detectorLock = new Object();
    private FaceDetectorYN detector;
    private String initializationError;

    @PostConstruct
    void initialize() {
        try {
            nu.pattern.OpenCV.loadLocally();
            Path modelFile = Files.createTempFile("wanyue-face-detection-", ".onnx");
            modelFile.toFile().deleteOnExit();
            try (var input = new ClassPathResource(MODEL).getInputStream()) {
                Files.copy(input, modelFile, StandardCopyOption.REPLACE_EXISTING);
            }
            detector = FaceDetectorYN.create(modelFile.toString(), "", new Size(320, 320), 0.75f, 0.3f, 5000);
        } catch (Throwable ex) {
            // Never prevent the whole management platform from starting merely
            // because the native detector cannot be loaded on a new host.
            initializationError = ex.getClass().getSimpleName();
        }
    }

    public Map<String, Object> check(byte[] jpeg) {
        if (detector == null) return basicFallback(jpeg);
        try {
            return checkWithOpenCv(jpeg);
        } catch (Throwable ex) {
            initializationError = ex.getClass().getSimpleName();
            return basicFallback(jpeg);
        }
    }

    private Map<String, Object> checkWithOpenCv(byte[] jpeg) {
        Mat image = Imgcodecs.imdecode(new MatOfByte(jpeg), Imgcodecs.IMREAD_COLOR);
        if (image.empty()) return failed("图片文件无法识别", "DECODE");
        try {
            int width = image.cols(), height = image.rows();
            if (width < 400 || height < 400) return failed("照片分辨率过低，请使用不低于400×400的照片", "RESOLUTION");

            Mat detectionImage = image;
            double detectionScale = Math.min(1d, MAX_DETECTION_EDGE / (double) Math.max(width, height));
            if (detectionScale < 1d) {
                detectionImage = new Mat();
                Imgproc.resize(image, detectionImage, new Size(
                        Math.max(1, Math.round(width * detectionScale)),
                        Math.max(1, Math.round(height * detectionScale))), 0, 0, Imgproc.INTER_AREA);
            }
            Mat faces = new Mat();
            try {
                synchronized (detectorLock) {
                    detector.setInputSize(detectionImage.size());
                    detector.detect(detectionImage, faces);
                }
                if (faces.rows() == 0) return failed("未检测到清晰正脸，请正对镜头并露出完整五官", "FACE_NOT_FOUND");
                if (faces.rows() > 1) return failed("照片中检测到多张人脸，请只保留申请人本人", "MULTIPLE_FACES");

                float[] values = new float[(int) (faces.total() * faces.channels())];
                faces.get(0, 0, values);
                Rect detectedBox = safeRect(values, detectionImage.cols(), detectionImage.rows());
                double ratio = (double) detectedBox.area() / (detectionImage.cols() * (double) detectionImage.rows());
                if (ratio < 0.08) return failed("人脸占画面比例过小，请靠近镜头重新拍摄", "FACE_TOO_SMALL");
                if (ratio > 0.82) return failed("人脸距离镜头过近，请保持适当距离", "FACE_TOO_LARGE");

                Map<String, Object> poseFailure = checkPose(values, detectedBox);
                if (poseFailure != null) return poseFailure;

                Rect box = detectionScale < 1d
                        ? scaleRect(detectedBox, 1d / detectionScale, width, height)
                        : detectedBox;
                Mat face = new Mat(image, box);
                try {
                    Mat gray = new Mat();
                    Mat laplacian = new Mat();
                    try {
                        Imgproc.cvtColor(face, gray, Imgproc.COLOR_BGR2GRAY);
                        Scalar brightness = Core.mean(gray);
                        if (brightness.val[0] < 45) return failed("照片过暗，请在光线充足处重新拍摄", "TOO_DARK");
                        if (brightness.val[0] > 220) return failed("照片过亮，请避免强光或逆光", "TOO_BRIGHT");
                        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
                        MatOfDouble mean = new MatOfDouble(), deviation = new MatOfDouble();
                        try {
                            Core.meanStdDev(laplacian, mean, deviation);
                            double sharpness = Math.pow(deviation.get(0, 0)[0], 2);
                            if (sharpness < MIN_SHARPNESS) {
                                Map<String, Object> result = failed("照片清晰度不足，请保持手机稳定并重新拍摄", "BLURRY");
                                result.put("sharpness", Math.round(sharpness));
                                result.put("requiredSharpness", Math.round(MIN_SHARPNESS));
                                return result;
                            }
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("passed", true);
                            result.put("message", "人脸照片质量检测通过");
                            result.put("mode", "SYSTEM");
                            result.put("faceCount", 1);
                            result.put("faceRatio", Math.round(ratio * 1000d) / 1000d);
                            result.put("sharpness", Math.round(sharpness));
                            return result;
                        } finally {
                            mean.release();
                            deviation.release();
                        }
                    } finally {
                        gray.release();
                        laplacian.release();
                    }
                } finally {
                    face.release();
                }
            } finally {
                faces.release();
                if (detectionImage != image) detectionImage.release();
            }
        } finally {
            image.release();
        }
    }

    private Rect scaleRect(Rect source, double scale, int width, int height) {
        int x = Math.max(0, Math.min(width - 1, (int) Math.round(source.x * scale)));
        int y = Math.max(0, Math.min(height - 1, (int) Math.round(source.y * scale)));
        int w = Math.max(1, Math.min(width - x, (int) Math.round(source.width * scale)));
        int h = Math.max(1, Math.min(height - y, (int) Math.round(source.height * scale)));
        return new Rect(x, y, w, h);
    }

    private Map<String, Object> basicFallback(byte[] jpeg) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (image == null) return failed("图片文件无法识别", "DECODE");
            if (image.getWidth() < 400 || image.getHeight() < 400)
                return failed("照片分辨率过低，请使用不低于400×400的照片", "RESOLUTION");
            long light = 0, edge = 0, samples = 0;
            for (int y = 2; y < image.getHeight(); y += 2) {
                for (int x = 2; x < image.getWidth(); x += 2) {
                    int current = gray(image.getRGB(x, y));
                    light += current;
                    edge += Math.abs(current - gray(image.getRGB(x - 2, y)));
                    edge += Math.abs(current - gray(image.getRGB(x, y - 2)));
                    samples++;
                }
            }
            double brightness = samples == 0 ? 0 : light / (double) samples;
            double detail = samples == 0 ? 0 : edge / (double) (samples * 2);
            if (brightness < 45) return failed("照片过暗，请在光线充足处重新拍摄", "TOO_DARK");
            if (brightness > 220) return failed("照片过亮，请避免强光或逆光", "TOO_BRIGHT");
            if (detail < 7) return failed("照片模糊或缺少细节，请重新拍摄", "BLURRY");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("passed", true);
            result.put("message", "照片基础质量检测通过，将由审批人复核人脸");
            result.put("mode", "SYSTEM_FALLBACK");
            result.put("reviewRequired", true);
            return result;
        } catch (IOException ex) {
            return failed("图片文件无法识别", "DECODE");
        }
    }

    private int gray(int rgb) {
        int r = (rgb >>> 16) & 0xff, g = (rgb >>> 8) & 0xff, b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private Rect safeRect(float[] v, int width, int height) {
        int x = Math.max(0, Math.round(v[0]));
        int y = Math.max(0, Math.round(v[1]));
        int w = Math.max(1, Math.min(Math.round(v[2]), width - x));
        int h = Math.max(1, Math.min(Math.round(v[3]), height - y));
        return new Rect(x, y, w, h);
    }

    private Map<String, Object> checkPose(float[] v, Rect box) {
        if (v.length < 14) return null;
        double rightEyeX = v[4], rightEyeY = v[5], leftEyeX = v[6], leftEyeY = v[7];
        double noseX = v[8];
        double eyeTilt = Math.abs(rightEyeY - leftEyeY) / box.height;
        double eyeDistance = Math.abs(leftEyeX - rightEyeX) / box.width;
        double noseOffset = Math.abs(noseX - (rightEyeX + leftEyeX) / 2d) / box.width;
        if (eyeTilt > 0.13) return failed("头部倾斜角度较大，请保持头部端正", "POSE_TILT");
        if (eyeDistance < 0.20 || noseOffset > 0.18) return failed("人脸侧转角度较大，请正对镜头", "POSE_SIDE");
        return null;
    }

    private Map<String, Object> failed(String message, String step) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", false);
        result.put("message", message);
        result.put("failedStep", step);
        result.put("mode", "SYSTEM");
        return result;
    }
}
