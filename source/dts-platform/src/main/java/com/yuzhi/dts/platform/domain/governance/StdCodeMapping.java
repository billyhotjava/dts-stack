package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "std_code_mapping")
public class StdCodeMapping extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "map_id")
    private Long mapId;

    @Column(name = "code_type_id", length = 64, nullable = false)
    private String codeTypeId;

    @Column(name = "source_sys", length = 64, nullable = false)
    private String sourceSys;

    @Column(name = "src_code", length = 128, nullable = false)
    private String srcCode;

    @Column(name = "std_code", length = 128, nullable = false)
    private String stdCode;

    @Override
    public Long getId() {
        return mapId;
    }

    public Long getMapId() {
        return mapId;
    }

    public void setMapId(Long mapId) {
        this.mapId = mapId;
    }

    public String getCodeTypeId() {
        return codeTypeId;
    }

    public void setCodeTypeId(String codeTypeId) {
        this.codeTypeId = codeTypeId;
    }

    public String getSourceSys() {
        return sourceSys;
    }

    public void setSourceSys(String sourceSys) {
        this.sourceSys = sourceSys;
    }

    public String getSrcCode() {
        return srcCode;
    }

    public void setSrcCode(String srcCode) {
        this.srcCode = srcCode;
    }

    public String getStdCode() {
        return stdCode;
    }

    public void setStdCode(String stdCode) {
        this.stdCode = stdCode;
    }
}
