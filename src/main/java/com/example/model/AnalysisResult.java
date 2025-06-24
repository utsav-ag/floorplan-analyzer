package com.example.model;

import java.util.List;

public class AnalysisResult {
private List<BoothInfo> booths;
private LayoutCharacteristics layout_characteristics;

// getters and setters  
public List<BoothInfo> getBooths() {  
    return booths;  
}  
public void setBooths(List<BoothInfo> booths) {  
    this.booths = booths;  
}  
public LayoutCharacteristics getLayout_characteristics() {  
    return layout_characteristics;  
}  
public void setLayout_characteristics(LayoutCharacteristics layout_characteristics) {  
    this.layout_characteristics = layout_characteristics;  
}  
}
