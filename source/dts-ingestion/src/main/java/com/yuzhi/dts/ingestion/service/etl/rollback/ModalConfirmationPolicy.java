package com.yuzhi.dts.ingestion.service.etl.rollback;

import org.springframework.stereotype.Component;

@Component
public class ModalConfirmationPolicy implements ConfirmationPolicy {

	@Override
	public boolean requiresConfirmation(RollbackLevel level) {
		return true;
	}

	@Override
	public String confirmationType(RollbackLevel level) {
		return "MODAL";
	}
}
