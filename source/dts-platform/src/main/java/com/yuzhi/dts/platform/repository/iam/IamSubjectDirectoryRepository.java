package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamSubjectDirectoryRepository extends JpaRepository<IamSubjectDirectory, UUID> {
    List<IamSubjectDirectory> findTop20BySubjectTypeIgnoreCaseAndDisplayNameIgnoreCaseContaining(String subjectType, String keyword);
    List<IamSubjectDirectory> findTop20BySubjectTypeIgnoreCaseAndSubjectIdIgnoreCaseContaining(String subjectType, String keyword);
}
