package me.kiporenko.warframefissureschecker;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void handleClientDisconnect(AsyncRequestNotUsableException e) {
		// Log at DEBUG level instead of ERROR to reduce noise
		logger.debug("Client disconnected during async request: {}", e.getMessage());
	}

	@ExceptionHandler(ClientAbortException.class)
	public void handleClientAbort(ClientAbortException e) {
		logger.debug("Client aborted connection: {}", e.getMessage());
	}

	@ExceptionHandler(java.io.IOException.class)
	public void handleIOException(java.io.IOException e) {
		// Handle "connection reset" and similar IO exceptions
		if (e.getMessage() != null &&
				(e.getMessage().contains("Connection reset") ||
						e.getMessage().contains("Broken pipe") ||
						e.getMessage().contains("разорвала установленное подключение"))) {
			logger.debug("Client connection lost: {}", e.getMessage());
		} else {
			logger.warn("IO Exception occurred: {}", e.getMessage());
		}
	}
}