package com.insuscan.enums;


public enum ReferenceObjectDimensions {

    INSULIN_SYRINGE("INSULIN_SYRINGE", 16.0f, 1.25f, 1.25f,
        "INSULIN PEN — a slim cylindrical medical pen (like a NovoPen or FlexPen), approximately 16.0cm long and 1.25cm in diameter. It looks like a thick marker or a large pen, placed next to the plate."),
    CARD("CARD", 8.56f, 5.398f, 5.398f,
        "CREDIT CARD / ID CARD — width=8.56cm, height=5.398cm"),
    NONE("NONE", 0f, 0f, 0f, "");

    private final String serverValue;
    private final float lengthCm;
    private final float widthCm;
    private final float sideViewDimensionCm;
    private final String scaleDescription;

    ReferenceObjectDimensions(String serverValue, float lengthCm, float widthCm, float sideViewDimensionCm, String scaleDescription) {
        this.serverValue = serverValue;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.sideViewDimensionCm = sideViewDimensionCm;
        this.scaleDescription = scaleDescription;
    }

    public float getLengthCm() { return lengthCm; }
    public float getWidthCm()  { return widthCm; }
    public float getSideViewDimensionCm() { return sideViewDimensionCm; }
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