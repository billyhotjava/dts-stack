package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.util.List;
import org.springframework.stereotype.Component;

/** Read-only adapter exposing the canonical ModelSpec update validation to the authoring facade. */
@Component
public class ModelAuthoringModelValidator {

    public List<FieldIssue> validate(UpdateModelSpecCommand command) {
        return ModelSpecContract.validateUpdate(command);
    }
}
