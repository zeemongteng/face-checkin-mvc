package com.example.facecheckin.service;

import com.example.facecheckin.model.AlgorithmResult;
import com.example.facecheckin.model.CheckInRecord;
import com.example.facecheckin.model.StudentProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class CheckInService {

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StudentProfile> students = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<CheckInRecord> attendanceHistory = new CopyOnWriteArrayList<>();

    @Value("${app.python.executable:python3}")
    private String pythonExecutable;

    @Value("${app.python.script:python/face_engine.py}")
    private String pythonScriptPath;

    @Value("${app.storage.dir:data/students}")
    private String storageDir;

    @Value("${app.classroom.name:ห้องเรียน SC-9101 (Smart Classroom)}")
    private String classroomName;

    @Value("${app.classroom.latitude:16.4745}")
    private double classroomLat;

    @Value("${app.classroom.longitude:102.8230}")
    private double classroomLng;

    @Value("${app.classroom.radius-meters:150.0}")
    private double allowedRadiusMeters;

    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(storageDir).toAbsolutePath();
            Files.createDirectories(dir);
            Path metaFile = dir.resolve("students.json");
            if (Files.exists(metaFile)) {
                List<StudentProfile> loaded = objectMapper.readValue(
                        metaFile.toFile(), new TypeReference<List<StudentProfile>>() {});
                for (StudentProfile s : loaded) {
                    students.put(s.getStudentId(), s);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveStudentsMetadata() {
        try {
            Path dir = Paths.get(storageDir).toAbsolutePath();
            Files.createDirectories(dir);
            Path metaFile = dir.resolve("students.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), new ArrayList<>(students.values()));
        } catch (Exception ignored) {
        }
    }

    public StudentProfile registerStudent(String studentId, String fullName, byte[] imageBytes, String mimeType) throws Exception {
        Path dir = Paths.get(storageDir).toAbsolutePath();
        Files.createDirectories(dir);

        String safeId = studentId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path imgFile = dir.resolve(safeId + ".jpg");
        Files.write(imgFile, imageBytes);

        String base64 = "data:" + (mimeType != null ? mimeType : "image/jpeg") + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);

        StudentProfile profile = new StudentProfile(
                studentId.trim(),
                fullName.trim(),
                imgFile.toString(),
                base64,
                LocalDateTime.now().format(TIME_FMT)
        );
        students.put(profile.getStudentId(), profile);
        saveStudentsMetadata();
        return profile;
    }

    public Collection<StudentProfile> getAllStudents() {
        return students.values();
    }

    public StudentProfile getStudent(String studentId) {
        return students.get(studentId);
    }

    public List<CheckInRecord> getAttendanceHistory() {
        return attendanceHistory;
    }

    public void updateClassroomGeofence(String name, double lat, double lng, double radiusMeters) {
        if (name != null && !name.trim().isEmpty()) {
            this.classroomName = name.trim();
        }
        this.classroomLat = lat;
        this.classroomLng = lng;
        this.allowedRadiusMeters = radiusMeters;
    }

    /**
     * คำนวณระยะห่างระหว่างพิกัด GPS 2 จุดด้วยสูตร Haversine (หน่วยเป็นเมตร)
     */
    public double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_METERS * c * 100.0) / 100.0;
    }

    public CheckInRecord processCheckIn(
            String studentId,
            double userLat,
            double userLng,
            String verificationPolicy,
            byte[] liveFaceBytes,
            String mimeType) {

        CheckInRecord record = new CheckInRecord();
        record.setCheckInTime(LocalDateTime.now().format(TIME_FMT));
        record.setStudentId(studentId);
        record.setLatitude(userLat);
        record.setLongitude(userLng);
        record.setAllowedRadiusMeters(allowedRadiusMeters);
        record.setSelectedPolicy(verificationPolicy);

        StudentProfile student = students.get(studentId);
        if (student == null) {
            record.setStudentName("ไม่พบข้อมูลนักศึกษา");
            record.setOverallSuccess(false);
            record.setSummaryMessage("ไม่พบรหัสนักศึกษา " + studentId + " ในระบบ กรุณาลงทะเบียนใบหน้าก่อนเช็คชื่อ");
            return record;
        }
        record.setStudentName(student.getFullName());

        // 1. ตรวจสอบพิกัด GPS (Location Geofencing)
        double distanceMeters = calculateHaversineDistanceMeters(classroomLat, classroomLng, userLat, userLng);
        record.setDistanceMeters(distanceMeters);
        boolean locationOk = distanceMeters <= allowedRadiusMeters;
        record.setLocationVerified(locationOk);

        // 2. ตรวจสอบใบหน้าด้วย 3 อัลกอริทึม (LBPH, Dlib ResNet-34, InsightFace ArcFace)
        String capturedBase64 = "data:" + (mimeType != null ? mimeType : "image/jpeg") + ";base64,"
                + Base64.getEncoder().encodeToString(liveFaceBytes);
        record.setCapturedImageBase64(capturedBase64);

        Path tempTarget = null;
        try {
            tempTarget = Files.createTempFile("checkin_scan_", ".jpg");
            Files.write(tempTarget, liveFaceBytes);

            List<AlgorithmResult> algoResults = runFaceComparison(Paths.get(student.getImagePath()), tempTarget);
            record.setAlgorithmResults(algoResults);

            boolean faceOk = evaluateFacePolicy(algoResults, verificationPolicy);
            record.setFaceVerified(faceOk);

            // ต้องผ่านทั้ง Location (GPS) และ Face Scan จึงจะเช็คชื่อสำเร็จ
            boolean overall = locationOk && faceOk;
            record.setOverallSuccess(overall);

            if (overall) {
                record.setSummaryMessage("เช็คชื่อเข้าเรียนสำเร็จ! (ยืนยันทั้งพิกัด GPS และใบหน้าผ่านเกณฑ์)");
            } else if (!locationOk && !faceOk) {
                record.setSummaryMessage("เช็คชื่อไม่สำเร็จ: อยู่นอกรัศมีห้องเรียน (" + distanceMeters + " ม.) และใบหน้าไม่ตรงกับที่ลงทะเบียน");
            } else if (!locationOk) {
                record.setSummaryMessage("เช็คชื่อไม่สำเร็จ: ใบหน้าถูกต้อง แต่คุณอยู่นอกรัศมีห้องเรียน (ห่าง " + distanceMeters + " ม. เกินกำหนด " + allowedRadiusMeters + " ม.)");
            } else {
                record.setSummaryMessage("เช็คชื่อไม่สำเร็จ: อยู่ในพื้นที่ห้องเรียน แต่การสแกนใบหน้าไม่ผ่านเกณฑ์ (" + verificationPolicy + ")");
            }

            attendanceHistory.add(0, record);
            return record;
        } catch (Exception e) {
            record.setFaceVerified(false);
            record.setOverallSuccess(false);
            record.setSummaryMessage("เกิดข้อผิดพลาดขณะสแกนใบหน้า: " + e.getMessage());
            return record;
        } finally {
            try {
                if (tempTarget != null) Files.deleteIfExists(tempTarget);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean evaluateFacePolicy(List<AlgorithmResult> results, String policy) {
        if (results == null || results.isEmpty()) return false;

        int matchCount = 0;
        boolean lbphMatch = false;
        boolean dlibMatch = false;
        boolean arcfaceMatch = false;

        for (AlgorithmResult r : results) {
            if (r.isMatch()) matchCount++;
            String name = r.getName() != null ? r.getName().toLowerCase() : "";
            if (name.contains("lbph") && r.isMatch()) lbphMatch = true;
            if (name.contains("dlib") && r.isMatch()) dlibMatch = true;
            if ((name.contains("arcface") || name.contains("insightface")) && r.isMatch()) arcfaceMatch = true;
        }

        if ("ARCFACE".equalsIgnoreCase(policy)) {
            return arcfaceMatch;
        } else if ("DLIB".equalsIgnoreCase(policy)) {
            return dlibMatch;
        } else if ("LBPH".equalsIgnoreCase(policy)) {
            return lbphMatch;
        } else if ("ALL".equalsIgnoreCase(policy)) {
            return matchCount == 3;
        }
        // ค่าเริ่มต้น: MAJORITY (ผ่านอย่างน้อย 2 ใน 3 อัลกอริทึม)
        return matchCount >= 2;
    }

    public List<AlgorithmResult> runFaceComparison(Path referenceImage, Path targetImage) {
        try {
            File scriptFile = new File(pythonScriptPath).getAbsoluteFile();
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable,
                    scriptFile.getAbsolutePath(),
                    "compare",
                    "--ref", referenceImage.toAbsolutePath().toString(),
                    "--target", targetImage.toAbsolutePath().toString()
            );

            Process process = pb.start();
            String stdout;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = reader.lines().collect(Collectors.joining("\n")).trim();
            }

            String stderr;
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                stderr = errReader.lines().collect(Collectors.joining("\n")).trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || stdout.isEmpty()) {
                AlgorithmResult err = new AlgorithmResult();
                err.setName("Python Engine Error");
                err.setStatus("ERROR");
                err.setMessage("รัน Python ไม่สำเร็จ: " + (stderr.isEmpty() ? "Exit code " + exitCode : stderr));
                return List.of(err);
            }

            String[] lines = stdout.split("\\R");
            String jsonLine = lines[lines.length - 1];
            JsonNode root = objectMapper.readTree(jsonLine);
            JsonNode algosNode = root.get("algorithms");
            if (algosNode != null && algosNode.isArray()) {
                return objectMapper.convertValue(algosNode, new TypeReference<List<AlgorithmResult>>() {});
            }
            return new ArrayList<>();
        } catch (Exception e) {
            AlgorithmResult err = new AlgorithmResult();
            err.setName("System Error");
            err.setStatus("ERROR");
            err.setMessage(e.getMessage());
            return List.of(err);
        }
    }

    public String getClassroomName() {
        return classroomName;
    }

    public double getClassroomLat() {
        return classroomLat;
    }

    public double getClassroomLng() {
        return classroomLng;
    }

    public double getAllowedRadiusMeters() {
        return allowedRadiusMeters;
    }
}
