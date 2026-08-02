import { listIndicators, listStandards } from "@/api/platformApi";

/**
 * Read-only bridge for the modeling overview. The overview owns no metric or
 * standard ledger; it only consumes the canonical governance catalog.
 */
export const listStandardsForModelingOverview = () => listStandards({ page: 0, size: 500 });

export const listIndicatorsForModelingOverview = () => listIndicators({ page: 0, size: 500 });
