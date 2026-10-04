package com.yuzhi.dts.analytics.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.UnexpectedRollbackException;

class DataLakeInitializationRetryTest {

    private final DataLakeDatabaseInitializer initializer = mock(DataLakeDatabaseInitializer.class);
    private final DataLakeInitializationRetry retry = new DataLakeInitializationRetry(initializer);

    @Test
    void doesNotInitializeBeforeApplicationIsReady() {
        retry.retryInitialization();
        verifyNoInteractions(initializer);
    }

    @Test
    void retriesAfterPlatformRecoversAndStopsAfterRegistration() {
        when(initializer.initializeDataLake()).thenReturn(false, true);

        retry.onApplicationReady();
        retry.retryInitialization();
        retry.retryInitialization();
        retry.onApplicationReady();

        verify(initializer, times(2)).initializeDataLake();
    }

    @Test
    void transactionCommitFailureDoesNotCountAsSuccessfulRegistration() {
        when(initializer.initializeDataLake())
            .thenThrow(new UnexpectedRollbackException("commit failed"))
            .thenReturn(true);

        retry.onApplicationReady();
        retry.retryInitialization();
        retry.retryInitialization();

        verify(initializer, times(2)).initializeDataLake();
    }

    @Test
    void stopsAfterSixFailedAttemptsEvenIfReadyEventIsRepeated() {
        when(initializer.initializeDataLake()).thenReturn(false);

        retry.onApplicationReady();
        for (int i = 0; i < 10; i++) {
            retry.retryInitialization();
        }
        retry.onApplicationReady();

        verify(initializer, times(6)).initializeDataLake();
    }

    @Test
    void repeatedTriggerDoesNotReinitializeAnExistingRegistration() {
        when(initializer.initializeDataLake()).thenReturn(true);

        retry.onApplicationReady();
        retry.onApplicationReady();
        retry.retryInitialization();

        verify(initializer).initializeDataLake();
    }
}
