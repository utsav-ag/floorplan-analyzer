package com.example.model;

import java.util.List;

public class AdditionalInfo {
private String company_name;
private String booth_size;
private List<String> features;

// getters and setters  
public String getCompany_name() {  
    return company_name;  
}  
public void setCompany_name(String company_name) {  
    this.company_name = company_name;  
}  
public String getBooth_size() {  
    return booth_size;  
}  
public void setBooth_size(String booth_size) {  
    this.booth_size = booth_size;  
}  
public List<String> getFeatures() {  
    return features;  
}  
public void setFeatures(List<String> features) {  
    this.features = features;  
}  
}

