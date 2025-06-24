package com.example.model;

public class StoredImageResponse {
    private String id;
    private String imageUrl;
    private String modelUsed;
    private String scannedAt;
    private AnalysisResult analysisResult;
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public String getScannedAt() { return scannedAt; }
    public void setScannedAt(String scannedAt) { this.scannedAt = scannedAt; }
    public AnalysisResult getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(AnalysisResult analysisResult) { this.analysisResult = analysisResult; }
}
