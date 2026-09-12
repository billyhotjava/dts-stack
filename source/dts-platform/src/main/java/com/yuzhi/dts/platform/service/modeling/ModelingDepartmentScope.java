package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.modeling.ModelingIdentity;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingDepartment;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelingDepartmentScope {
    private final AdminDirectoryGateway directory;
    public ModelingDepartmentScope(AdminDirectoryGateway directory) { this.directory = directory; }
    public List<ModelingDepartment> departments() {
        var user = ModelingIdentity.current();
        if (!ModelingIdentity.institute(user)) {
            if (user.deptCode() == null) return List.of();
            return List.of(new ModelingDepartment(user.deptCode(), user.deptName()));
        }
        try { return directory.modelingDepartments(); }
        catch (RuntimeException ex) { throw new ModelingIdentityException(503, "MODELING_IDENTITY_UNAVAILABLE", "组织目录暂不可用，请稍后重试"); }
    }
    public String resolve(String requested) {
        var user = ModelingIdentity.current();
        if (!ModelingIdentity.institute(user)) {
            if (user.deptCode() == null || user.deptCode().isBlank()) throw invalid("MODELING_DEPARTMENT_REQUIRED", "账号尚未关联有效部门");
            if (requested != null && !requested.isBlank() && !requested.equals(user.deptCode())) {
                throw new ModelSpecException("MODELING_DEPARTMENT_FORBIDDEN", "只能维护本部门公共层", ModelSpecException.Kind.FORBIDDEN);
            }
            return user.deptCode();
        }
        if (requested == null || requested.isBlank()) throw invalid("MODELING_DEPARTMENT_REQUIRED", "请先选择建模部门");
        return departments().stream().filter(department -> department.code().equals(requested)).map(ModelingDepartment::code)
            .findFirst().orElseThrow(() -> invalid("MODELING_DEPARTMENT_INVALID", "请选择有效部门"));
    }
    private static ModelSpecException invalid(String code, String message) { return new ModelSpecException(code, message, ModelSpecException.Kind.BAD_REQUEST); }
}
