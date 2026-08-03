package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.application.ingestion.handler.IngestionHandler;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-158: {@link IngestionService}'s own handler-registry/dispatch contract, found
 * during the ADR-011 review to have no dedicated unit test - it is covered only
 * transitively today, through tests of other classes that happen to use a real
 * {@link IngestionService} for other reasons ({@code ScheduledIngestionDispatcherTest},
 * {@code ParcialConcurrentIngestionAppTest}).
 */
class IngestionServiceTest {

    private IngestionHandler handlerFor(String methodName) {
        IngestionHandler handler = mock(IngestionHandler.class);
        when(handler.getMethodName()).thenReturn(methodName);
        return handler;
    }

    @Test
    @DisplayName("execute dispatches to the handler registered for that exact method name, no other")
    void execute_dispatchesToCorrectHandler() throws Exception {
        IngestionHandler ciudadHandler = handlerFor("promediosSipsaCiudad");
        IngestionHandler parcialHandler = handlerFor("promediosSipsaParcial");
        IngestionService service = new IngestionService(List.of(ciudadHandler, parcialHandler));
        IngestionContext context = mock(IngestionContext.class);

        service.execute("promediosSipsaParcial", context);

        verify(parcialHandler).execute(context);
        verify(ciudadHandler, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("execute on an unregistered method name throws SipsaBusinessException, no handler invoked")
    void execute_unregisteredMethod_throws() throws Exception {
        IngestionHandler ciudadHandler = handlerFor("promediosSipsaCiudad");
        IngestionService service = new IngestionService(List.of(ciudadHandler));
        IngestionContext context = mock(IngestionContext.class);

        assertThatThrownBy(() -> service.execute("unknownMethod", context))
                .isInstanceOf(SipsaBusinessException.class)
                .hasMessageContaining("unknownMethod");

        verify(ciudadHandler, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("execute with a null or blank method name throws SipsaBusinessException")
    void execute_nullOrBlankMethodName_throws() {
        IngestionService service = new IngestionService(List.of(handlerFor("promediosSipsaCiudad")));
        IngestionContext context = mock(IngestionContext.class);

        assertThatThrownBy(() -> service.execute(null, context)).isInstanceOf(SipsaBusinessException.class);
        assertThatThrownBy(() -> service.execute("  ", context)).isInstanceOf(SipsaBusinessException.class);
    }

    @Test
    @DisplayName("execute with a null context throws SipsaBusinessException")
    void execute_nullContext_throws() {
        IngestionService service = new IngestionService(List.of(handlerFor("promediosSipsaCiudad")));

        assertThatThrownBy(() -> service.execute("promediosSipsaCiudad", null))
                .isInstanceOf(SipsaBusinessException.class);
    }

    @Test
    @DisplayName("isValidMethod / getAvailableMethodNames reflect exactly the registered handlers")
    void isValidMethod_andAvailableNames_reflectRegisteredHandlers() {
        IngestionService service = new IngestionService(
                List.of(handlerFor("promediosSipsaCiudad"), handlerFor("promediosSipsaParcial")));

        assertThat(service.isValidMethod("promediosSipsaCiudad")).isTrue();
        assertThat(service.isValidMethod("promediosSipsaParcial")).isTrue();
        assertThat(service.isValidMethod("promediosSipsaMesMadr")).isFalse();
        assertThat(service.isValidMethod(null)).isFalse();
        assertThat(service.getAvailableMethodNames())
                .containsExactlyInAnyOrder("promediosSipsaCiudad", "promediosSipsaParcial");
    }

    @Test
    @DisplayName("a null or empty handler list registers no methods (isValidMethod always false)")
    void noHandlers_registersNothing() {
        assertThat(new IngestionService(null).getAvailableMethodNames()).isEmpty();
        assertThat(new IngestionService(List.of()).isValidMethod("anything")).isFalse();
    }

    @Test
    @DisplayName("validateTriggerRequest: null or blank method -> SipsaIngestionValidationException")
    void validateTriggerRequest_nullOrBlank_throws() {
        IngestionService service = new IngestionService(List.of(handlerFor("promediosSipsaCiudad")));

        assertThatThrownBy(() -> service.validateTriggerRequest(null))
                .isInstanceOf(SipsaIngestionValidationException.class);
        assertThatThrownBy(() -> service.validateTriggerRequest(" "))
                .isInstanceOf(SipsaIngestionValidationException.class);
    }

    @Test
    @DisplayName("validateTriggerRequest: unregistered method -> SipsaIngestionValidationException")
    void validateTriggerRequest_unregisteredMethod_throws() {
        IngestionService service = new IngestionService(List.of(handlerFor("promediosSipsaCiudad")));

        assertThatThrownBy(() -> service.validateTriggerRequest("promediosSipsaParcial"))
                .isInstanceOf(SipsaIngestionValidationException.class);
    }

    @Test
    @DisplayName("validateTriggerRequest: a registered method does not throw")
    void validateTriggerRequest_registeredMethod_doesNotThrow() {
        IngestionService service = new IngestionService(List.of(handlerFor("promediosSipsaCiudad")));

        assertThatCode(() -> service.validateTriggerRequest("promediosSipsaCiudad")).doesNotThrowAnyException();
    }
}
