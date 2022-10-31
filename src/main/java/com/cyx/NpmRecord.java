package com.cyx;

public class NpmRecord {
    private Integer id;
    private String name;
    private String version;
    private Integer publish;

    public NpmRecord() {
    }

    public NpmRecord(String name, String version) {
        this.name = name;
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NpmRecord)) return false;

        NpmRecord npmRecord = (NpmRecord) o;

        if (!getName().equals(npmRecord.getName())) return false;
        return getVersion().equals(npmRecord.getVersion());
    }

    @Override
    public int hashCode() {
        int result = getName().hashCode();
        result = 31 * result + getVersion().hashCode();
        return result;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getPublish() {
        return publish;
    }

    public void setPublish(Integer publish) {
        this.publish = publish;
    }
}
