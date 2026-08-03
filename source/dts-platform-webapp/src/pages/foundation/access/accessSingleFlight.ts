export type SingleFlightRef = { current: boolean };

export const acquireSingleFlight = (lock: SingleFlightRef) => {
	if (lock.current) return false;
	lock.current = true;
	return true;
};

export const releaseSingleFlight = (lock: SingleFlightRef) => {
	lock.current = false;
};

export type OwnedSingleFlightRef<Owner> = { current: Owner | null };

export const acquireOwnedSingleFlight = <Owner>(lock: OwnedSingleFlightRef<Owner>, owner: Owner) => {
	if (lock.current !== null) return false;
	lock.current = owner;
	return true;
};

export const ownsSingleFlight = <Owner>(lock: OwnedSingleFlightRef<Owner>, owner: Owner) => lock.current === owner;

export const releaseOwnedSingleFlight = <Owner>(lock: OwnedSingleFlightRef<Owner>, owner: Owner) => {
	if (!ownsSingleFlight(lock, owner)) return false;
	lock.current = null;
	return true;
};

export const resetOwnedSingleFlight = <Owner>(lock: OwnedSingleFlightRef<Owner>) => {
	lock.current = null;
};
