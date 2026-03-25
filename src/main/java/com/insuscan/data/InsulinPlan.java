package com.insuscan.data;

public class InsulinPlan {

    private String id;
    private String name;
    private boolean isDefault;
    private Float icr;
    private Float isf;
    private Integer targetGlucose;

    public InsulinPlan() {}

    public InsulinPlan(String id, String name, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public Float getIcr() { return icr; }
    public void setIcr(Float icr) { this.icr = icr; }

    public Float getIsf() { return isf; }
    public void setIsf(Float isf) { this.isf = isf; }

    public Integer getTargetGlucose() { return targetGlucose; }
    public void setTargetGlucose(Integer targetGlucose) { this.targetGlucose = targetGlucose; }

    @Override
    public String toString() {
        return "InsulinPlan{name='" + name + "', isDefault=" + isDefault + "}";
    }
}