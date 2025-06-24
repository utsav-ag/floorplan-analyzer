package com.example.model;

import java.util.List;

public class Coordinates {
private List<Double> top_left;
private List<Double> top_right;
private List<Double> bottom_left;
private List<Double> bottom_right;

// getters and setters  
public List<Double> getTop_left() {  
    return top_left;  
}  
public void setTop_left(List<Double> top_left) {  
    this.top_left = top_left;  
}  
public List<Double> getTop_right() {  
    return top_right;  
}  
public void setTop_right(List<Double> top_right) {  
    this.top_right = top_right;  
}  
public List<Double> getBottom_left() {  
    return bottom_left;  
}  
public void setBottom_left(List<Double> bottom_left) {  
    this.bottom_left = bottom_left;  
}  
public List<Double> getBottom_right() {  
    return bottom_right;  
}  
public void setBottom_right(List<Double> bottom_right) {  
    this.bottom_right = bottom_right;  
}  
}
