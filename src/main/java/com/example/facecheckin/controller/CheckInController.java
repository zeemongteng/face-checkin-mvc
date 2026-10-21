package com.example.facecheckin.controller;

import com.example.facecheckin.model.CheckInRecord;
import com.example.facecheckin.model.StudentProfile;
import com.example.facecheckin.service.CheckInService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Controller
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @GetMapping("/")
    public String index(Model model) {
        populateModel(model);
        return "index";
    }

    @PostMapping("/register-student")
    public String registerStudent(
            @RequestParam("studentId") String studentId,
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "faceFile", required = false) MultipartFile faceFile,
            @RequestParam(value = "webcamBase64", required = false) String webcamBase64,
            Model model) {

        try {
            byte[] imageBytes = extractImageBytes(faceFile, webcamBase64);
            String mimeType = (faceFile != null && !faceFile.isEmpty()) ? faceFile.getContentType() : "image/jpeg";

            if (imageBytes == null || imageBytes.length == 0) {
                model.addAttribute("registerError", "กรุณาถ่ายภาพจากกล้องเว็บแคม หรืออัปโหลดไฟล์ภาพใบหน้าเพื่อลงทะเบียน");
            } else {
                StudentProfile profile = checkInService.registerStudent(studentId, fullName, imageBytes, mimeType);
                model.addAttribute("registerSuccess", "ลงทะเบียนนักศึกษา " + profile.getStudentId() + " (" + profile.getFullName() + ") สำเร็จ!");
            }
        } catch (Exception e) {
            model.addAttribute("registerError", "เกิดข้อผิดพลาดในการลงทะเบียน: " + e.getMessage());
        }

        populateModel(model);
        return "index";
    }

    @PostMapping("/update-geofence")
    public String updateGeofence(
            @RequestParam("classroomName") String classroomName,
            @RequestParam("classroomLat") double classroomLat,
            @RequestParam("classroomLng") double classroomLng,
            @RequestParam("allowedRadiusMeters") double allowedRadiusMeters,
            Model model) {

        checkInService.updateClassroomGeofence(classroomName, classroomLat, classroomLng, allowedRadiusMeters);
        model.addAttribute("geofenceSuccess", "อัปเดตพิกัดห้องเรียน (" + classroomName + ") และรัศมี " + allowedRadiusMeters + " เมตร เรียบร้อยแล้ว!");
        populateModel(model);
        return "index";
    }

    @PostMapping("/checkin")
    public String checkIn(
            @RequestParam("studentId") String studentId,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam(value = "verificationPolicy", defaultValue = "MAJORITY") String verificationPolicy,
            @RequestParam(value = "liveFaceFile", required = false) MultipartFile liveFaceFile,
            @RequestParam(value = "liveWebcamBase64", required = false) String liveWebcamBase64,
            Model model) {

        try {
            if (latitude == null || longitude == null) {
                model.addAttribute("checkInError", "ไม่สามารถระบุพิกัด GPS ได้ กรุณากดปุ่ม 'ดึงพิกัด GPS ปัจจุบัน' ก่อนเช็คชื่อ");
                populateModel(model);
                return "index";
            }

            byte[] faceBytes = extractImageBytes(liveFaceFile, liveWebcamBase64);
            String mimeType = (liveFaceFile != null && !liveFaceFile.isEmpty()) ? liveFaceFile.getContentType() : "image/jpeg";

            if (faceBytes == null || faceBytes.length == 0) {
                model.addAttribute("checkInError", "กรุณาสแกนใบหน้าผ่านกล้องเว็บแคม หรืออัปโหลดภาพถ่ายใบหน้าขณะเช็คชื่อ");
                populateModel(model);
                return "index";
            }

            CheckInRecord record = checkInService.processCheckIn(
                    studentId, latitude, longitude, verificationPolicy, faceBytes, mimeType);
            model.addAttribute("latestCheckIn", record);

            StudentProfile registered = checkInService.getStudent(studentId);
            if (registered != null) {
                model.addAttribute("latestStudentProfile", registered);
            }
        } catch (Exception e) {
            model.addAttribute("checkInError", "เกิดข้อผิดพลาดในการเช็คชื่อ: " + e.getMessage());
        }

        populateModel(model);
        return "index";
    }

    private byte[] extractImageBytes(MultipartFile file, String base64DataUrl) throws Exception {
        if (base64DataUrl != null && base64DataUrl.startsWith("data:image")) {
            int commaIndex = base64DataUrl.indexOf(',');
            if (commaIndex != -1) {
                String base64Part = base64DataUrl.substring(commaIndex + 1);
                return Base64.getDecoder().decode(base64Part);
            }
        }
        if (file != null && !file.isEmpty()) {
            return file.getBytes();
        }
        return null;
    }

    private void populateModel(Model model) {
        model.addAttribute("students", checkInService.getAllStudents());
        model.addAttribute("attendanceHistory", checkInService.getAttendanceHistory());
        model.addAttribute("classroomName", checkInService.getClassroomName());
        model.addAttribute("classroomLat", checkInService.getClassroomLat());
        model.addAttribute("classroomLng", checkInService.getClassroomLng());
        model.addAttribute("allowedRadiusMeters", checkInService.getAllowedRadiusMeters());
    }
}
