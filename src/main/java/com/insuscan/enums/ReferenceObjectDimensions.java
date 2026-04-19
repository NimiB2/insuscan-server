package com.insuscan.enums;

public enum ReferenceObjectDimensions {

    INSULIN_SYRINGE("INSULIN_SYRINGE", 16.0f, 1.25f, "INSULIN SYRINGE — length=16.0cm, width=1.25cm"),
    SYRINGE_KNIFE("SYRINGE_KNIFE",     21.0f, 1.5f,  "SYRINGE KNIFE — length=21.0cm, width=1.5cm"),
    CARD("CARD",                        8.56f, 5.398f,"CREDIT CARD / ID CARD — width=8.56cm, height=5.398cm"),
    NONE("NONE",                        0f,    0f,    "");

    private final String serverValue;
    private final float lengthCm;
    private final float widthCm;
    private final String scaleDescription;

    ReferenceObjectDimensions(String serverValue, float lengthCm, float widthCm, String scaleDescription) {
        this.serverValue = serverValue;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.scaleDescription = scaleDescription;
    }

    public float getLengthCm() { return lengthCm; }
    public float getWidthCm()  { return widthCm; }
    public String getScaleDescription() { return scaleDescription; }

    public static ReferenceObjectDimensions fromServerValue(String value) {
        if (value == null) return NONE;
        for (ReferenceObjectDimensions d : values()) {
            if (d.serverValue.equalsIgnoreCase(value)) return d;
        }
        return NONE;
    }

    public static String getScaleDescription(String serverValue) {
        return fromServerValue(serverValue).scaleDescription;
    }
}