package com.example.facecheckin.model;

import java.util.List;

public class CheckInRecord {
    private String checkInTime;
    private String studentId;
    private String studentName;
    private double latitude;
    private double longitude;
    private double distanceMeters;
    private double allowedRadiusMeters;
    private boolean locationVerified;
    private String selectedPolicy;
    private boolean faceVerified;
    private boolean overallSuccess;
    private String summaryMessage;
    private String capturedImageBase64;
    private List<AlgorithmResult> algorithmResults;

    public CheckInRecord() {
    }

    public String getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(String checkInTime) {
        this.checkInTime = checkInTime;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public double getAllowedRadiusMeters() {
        return allowedRadiusMeters;
    }

    public void setAllowedRadiusMeters(double allowedRadiusMeters) {
        this.allowedRadiusMeters = allowedRadiusMeters;
    }

    public boolean isLocationVerified() {
        return locationVerified;
    }

    public void setLocationVerified(boolean locationVerified) {
        this.locationVerified = locationVerified;
    }

    public String getSelectedPolicy() {
        return selectedPolicy;
    }

    public void setSelectedPolicy(String selectedPolicy) {
        this.selectedPolicy = selectedPolicy;
    }

    public boolean isFaceVerified() {
        return faceVerified;
    }

    public void setFaceVerified(boolean faceVerified) {
        this.faceVerified = faceVerified;
    }

    public boolean isOverallSuccess() {
        return overallSuccess;
    }

    public void setOverallSuccess(boolean overallSuccess) {
        this.overallSuccess = overallSuccess;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public String getCapturedImageBase64() {
        return capturedImageBase64;
    }

    public void setCapturedImageBase64(String capturedImageBase64) {
        this.capturedImageBase64 = capturedImageBase64;
    }

    public List<AlgorithmResult> getAlgorithmResults() {
        return algorithmResults;
    }

    public void setAlgorithmResults(List<AlgorithmResult> algorithmResults) {
        this.algorithmResults = algorithmResults;
    }
}
