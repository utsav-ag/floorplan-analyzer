package com.example.model;

public class BoothInfo {
private String booth_number;
private double confidence;
private Coordinates coordinates;
private AdditionalInfo additional_info;

// getters and setters  
public String getBooth_number() {  
    return booth_number;  
}  
public void setBooth_number(String booth_number) {  
    this.booth_number = booth_number;  
}  
public double getConfidence() {  
    return confidence;  
}  
public void setConfidence(double confidence) {  
    this.confidence = confidence;  
}  
public Coordinates getCoordinates() {  
    return coordinates;  
}  
public void setCoordinates(Coordinates coordinates) {  
    this.coordinates = coordinates;  
}  
public AdditionalInfo getAdditional_info() {  
    return additional_info;  
}  
public void setAdditional_info(AdditionalInfo additional_info) {  
    this.additional_info = additional_info;  
}  
}