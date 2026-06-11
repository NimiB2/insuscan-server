package com.insuscan.enums;


public enum ReferenceObjectDimensions {

	INSULIN_PEN("INSULIN_PEN", 15.5f, 1.5f, 1.5f,
		    "INSULIN PEN — a slim cylindrical medical pen (like a NovoPen or FlexPen), approximately 15.5cm long and 1.5cm in diameter. It looks like a thick marker or a large pen, placed next to the plate."),
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
        if ("INSULIN_SYRINGE".equalsIgnoreCase(value)) return INSULIN_PEN;
        for (ReferenceObjectDimensions d : values()) {
            if (d.serverValue.equalsIgnoreCase(value)) return d;
        }
        return NONE;
    }

    public static String getScaleDescription(String serverValue) {
        return fromServerValue(serverValue).scaleDescription;
    }
}