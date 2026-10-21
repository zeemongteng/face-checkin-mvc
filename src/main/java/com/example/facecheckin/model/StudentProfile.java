package com.example.facecheckin.model;

public class StudentProfile {
    private String studentId;
    private String fullName;
    private String imagePath;
    private String previewBase64;
    private String registeredAt;

    public StudentProfile() {
    }

    public StudentProfile(String studentId, String fullName, String imagePath, String previewBase64, String registeredAt) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.imagePath = imagePath;
        this.previewBase64 = previewBase64;
        this.registeredAt = registeredAt;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getPreviewBase64() {
        return previewBase64;
    }

    public void setPreviewBase64(String previewBase64) {
        this.previewBase64 = previewBase64;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }
}
