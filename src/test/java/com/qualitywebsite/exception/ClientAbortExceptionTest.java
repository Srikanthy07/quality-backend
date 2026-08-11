package com.qualitywebsite.exception;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientAbortExceptionTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void testIsClientAbortException_TomcatClientAbortException() {
        ClientAbortException ex = new ClientAbortException(new IOException("An established connection was aborted by the software in your host machine"));
        assertTrue(GlobalExceptionHandler.isClientAbortException(ex));
    }

    @Test
    void testIsClientAbortException_AsyncRequestNotUsableException() {
        AsyncRequestNotUsableException ex = new AsyncRequestNotUsableException("Async step failed", new IOException("Broken pipe"));
        assertTrue(GlobalExceptionHandler.isClientAbortException(ex));
    }

    @Test
    void testIsClientAbortException_IOExceptionBrokenPipe() {
        IOException ex = new IOException("Broken pipe");
        assertTrue(GlobalExceptionHandler.isClientAbortException(ex));
    }

    @Test
    void testIsClientAbortException_IOExceptionConnectionReset() {
        IOException ex = new IOException("Connection reset by peer");
        assertTrue(GlobalExceptionHandler.isClientAbortException(ex));
    }

    @Test
    void testIsClientAbortException_GenuineServerException_ReturnsFalse() {
        NullPointerException npe = new NullPointerException("Null reference in controller");
        assertFalse(GlobalExceptionHandler.isClientAbortException(npe));

        IllegalStateException ise = new IllegalStateException("Database state invalid");
        assertFalse(GlobalExceptionHandler.isClientAbortException(ise));
    }

    @Test
    void testHandleClientAbort_ReturnsNullResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/analytics/recent");
        request.setQueryString("filter=30days");

        IOException clientAbort = new IOException("An established connection was aborted by the software in your host machine");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleClientAbort(clientAbort, request);
        assertNull(response, "Handler must return null body so Spring MVC does not attempt to serialize JSON to closed socket");
    }

    @Test
    void testHandleAllExceptions_GenuineError_Returns500Response() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/analytics/summary");

        NullPointerException npe = new NullPointerException("Simulated internal error");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAllExceptions(npe, request);
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Internal Server Error", response.getBody().get("error"));
    }

    @Test
    void testHandleNoResourceFound_Returns404Response() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/.well-known/appspecific/com.chrome.devtools.json");
        org.springframework.web.servlet.resource.NoResourceFoundException ex =
                new org.springframework.web.servlet.resource.NoResourceFoundException(org.springframework.http.HttpMethod.GET, ".well-known/appspecific/com.chrome.devtools.json");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleNoResourceFound(ex, request);
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
        assertEquals("Not Found", response.getBody().get("error"));
    }
}
