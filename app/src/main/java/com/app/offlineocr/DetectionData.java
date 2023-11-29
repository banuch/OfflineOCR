package com.app.offlineocr;

public class DetectionData {
    float left;
    String objectName;

    public DetectionData(float left, String objectName) {
        this.left = left;
        this.objectName = objectName;
    }

    public float getLeft() {
        return left;
    }

    public String getObjectName() {
        return objectName;
    }
}
