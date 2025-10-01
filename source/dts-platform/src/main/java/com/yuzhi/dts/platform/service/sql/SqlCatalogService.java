package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogNode;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogNodeType;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogRequest;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SqlCatalogService {

    public SqlCatalogNode fetchTree(SqlCatalogRequest request, Principal principal) {
        return new SqlCatalogNode(
            request.datasource() != null ? request.datasource() : "default",
            request.datasource() != null ? request.datasource() : "Default Datasource",
            SqlCatalogNodeType.CATALOG,
            List.of()
        );
    }
}
