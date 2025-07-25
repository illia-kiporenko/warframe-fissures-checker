package me.kiporenko.warframefissureschecker;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.catalina.connector.ClientAbortException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequestMapping("/fissures")
@CrossOrigin(originPatterns = "*") // Use originPatterns for better CORS handling
public class FissureController {

	private static final Logger logger = LoggerFactory.getLogger(FissureController.class);
	private static final int MAX_MISSION_TYPES = 10;
	private static final long TIMEOUT_MS = 30_000L;

	private final FissureService fissureService;

	public FissureController(FissureService fissureService) {
		this.fissureService = fissureService;
	}

	@GetMapping
	public DeferredResult<FissureResponse> getFissures(
			@RequestParam(required = false) List<String> missionTypes,
			@RequestParam(required = false) Boolean isHard,
			@RequestParam(required = false) String knownIds) {

		logger.info("Received long polling request for fissures with missionTypes: {}, isHard: {}, knownIds: {}",
				missionTypes, isHard, knownIds);

		try {
			validateInput(missionTypes);

			Set<String> expectedFissureIds = parseKnownIds(knownIds);
			FilterCriteria criteria = new FilterCriteria(missionTypes, isHard);

			return createDeferredResult(criteria, expectedFissureIds);

		} catch (ResponseStatusException e) {
			throw e; // Re-throw validation errors
		} catch (Exception e) {
			logger.error("Unexpected error in getFissures", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Internal server error: " + e.getMessage());
		}
	}

	@GetMapping("/immediate")
	public ResponseEntity<FissureResponse> getFissuresImmediate(
			@RequestParam(required = false) List<String> missionTypes,
			@RequestParam(required = false) Boolean isHard) {

		logger.info("Received immediate request for fissures with missionTypes: {} and isHard: {}",
				missionTypes, isHard);

		try {
			validateInput(missionTypes);

			FilterCriteria criteria = new FilterCriteria(missionTypes, isHard);
			List<Fissure> results = fissureService.getFissuresImmediate(criteria);

			FissureResponse response = createFissureResponse(results);
			logger.info("Returning {} fissures immediately for criteria: {}", results.size(), criteria);

			return ResponseEntity.ok()
					.header("Cache-Control", "no-cache, no-store, must-revalidate")
					.body(response);

		} catch (ResponseStatusException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Error in getFissuresImmediate", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping("/status")
	public ResponseEntity<StatusResponse> getStatus() {
		try {
			int activeListeners = fissureService.getActiveListenerCount();
			int currentFissures = fissureService.getCurrentFissures().size();

			StatusResponse status = new StatusResponse(
					"Fissure service is running",
					activeListeners,
					currentFissures,
					System.currentTimeMillis()
			);

			logger.debug("Status check: {} active listeners, {} current fissures",
					activeListeners, currentFissures);

			return ResponseEntity.ok()
					.header("Cache-Control", "no-cache")
					.body(status);
		} catch (Exception e) {
			logger.error("Error in getStatus", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new StatusResponse("Service error: " + e.getMessage(), 0, 0, System.currentTimeMillis()));
		}
	}

	@GetMapping("/test")
	public ResponseEntity<String> test() {
		logger.debug("Test endpoint called");
		return ResponseEntity.ok("Service is responding");
	}

	// Private helper methods
	private void validateInput(List<String> missionTypes) {
		if (missionTypes != null && missionTypes.size() > MAX_MISSION_TYPES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Maximum " + MAX_MISSION_TYPES + " mission types allowed per request");
		}
	}

	private Set<String> parseKnownIds(String knownIds) {
		if (knownIds == null || knownIds.trim().isEmpty()) {
			return null;
		}

		return Arrays.stream(knownIds.split(","))
				.map(String::trim)
				.filter(id -> !id.isEmpty())
				.collect(Collectors.toSet());
	}

	private FissureResponse createFissureResponse(List<Fissure> fissures) {
		Set<String> currentIds = fissures.stream()
				.map(Fissure::getId)
				.collect(Collectors.toSet());
		return new FissureResponse(fissures, currentIds);
	}

	private DeferredResult<FissureResponse> createDeferredResult(FilterCriteria criteria, Set<String> expectedFissureIds) {
		DeferredResult<FissureResponse> result = new DeferredResult<>(TIMEOUT_MS);
		DeferredResult<List<Fissure>> internalResult = new DeferredResult<>(TIMEOUT_MS);

		fissureService.registerListener(criteria, internalResult, expectedFissureIds);

		setupDeferredResultHandlers(result, internalResult, criteria);

		return result;
	}

	private void setupDeferredResultHandlers(DeferredResult<FissureResponse> result,
	                                         DeferredResult<List<Fissure>> internalResult,
	                                         FilterCriteria criteria) {

		// Convert internal result to response format
		internalResult.onCompletion(() -> {
			if (!result.isSetOrExpired()) {
				try {
					@SuppressWarnings("unchecked")
					List<Fissure> fissures = (List<Fissure>) internalResult.getResult();
					FissureResponse response = createFissureResponse(fissures);
					result.setResult(response);
				} catch (Exception e) {
					logger.error("Error creating response", e);
					result.setErrorResult(e);
				}
			}
		});

		// Handle internal result timeout
		internalResult.onTimeout(() -> {
			logger.info("Internal result timed out for criteria: {}", criteria);
			setTimeoutFallback(result, criteria);
		});

		internalResult.onError(throwable -> {
			if (!result.isSetOrExpired()) {
				logger.error("Error occurred for long polling request with criteria: {}", criteria, throwable);
				result.setErrorResult(throwable);
			}
		});

		// Handle main result timeout
		result.onTimeout(() -> {
			logger.info("Main result timed out for criteria: {}, ensuring response is set", criteria);
			setTimeoutFallback(result, criteria);
		});

		result.onCompletion(() -> {
			logger.debug("Long polling request completed for criteria: {}", criteria);
		});

		result.onError(throwable -> {
			if (!(throwable instanceof AsyncRequestNotUsableException) &&
					!(throwable instanceof ClientAbortException)) {
				logger.error("Error occurred for long polling request with criteria: {}", criteria, throwable);
			} else {
				logger.debug("Client disconnected during request for criteria: {}", criteria);
			}
		});
	}

	private void setTimeoutFallback(DeferredResult<FissureResponse> result, FilterCriteria criteria) {
		if (!result.isSetOrExpired()) {
			try {
				List<Fissure> currentFissures = fissureService.getFissuresImmediate(criteria);
				FissureResponse response = createFissureResponse(currentFissures);
				result.setResult(response);
				logger.info("Main timeout fallback: returning {} fissures", currentFissures.size());
			} catch (Exception e) {
				logger.error("Error in main timeout fallback", e);
				FissureResponse response = new FissureResponse(Collections.emptyList(), Collections.emptySet());
				result.setResult(response);
			}
		}
	}

	// Response wrapper to include fissure IDs for next request
	public static class FissureResponse {
		private final List<Fissure> fissures;
		private final Set<String> fissureIds;

		public FissureResponse(List<Fissure> fissures, Set<String> fissureIds) {
			this.fissures = fissures;
			this.fissureIds = fissureIds;
		}

		public List<Fissure> getFissures() {
			return fissures;
		}

		public Set<String> getFissureIds() {
			return fissureIds;
		}

		public String getFissureIdsAsString() {
			return String.join(",", fissureIds);
		}
	}

	// Status response for monitoring
	public static class StatusResponse {
		private final String message;
		private final int activeListeners;
		private final int currentFissures;
		private final long timestamp;

		public StatusResponse(String message, int activeListeners, int currentFissures, long timestamp) {
			this.message = message;
			this.activeListeners = activeListeners;
			this.currentFissures = currentFissures;
			this.timestamp = timestamp;
		}

		public String getMessage() { return message; }
		public int getActiveListeners() { return activeListeners; }
		public int getCurrentFissures() { return currentFissures; }
		public long getTimestamp() { return timestamp; }
	}

	// Exception handlers for client disconnects and async timeouts
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void handleClientDisconnect(AsyncRequestNotUsableException e) {
		logger.debug("Client disconnected during async request: {}", e.getMessage());
	}

	@ExceptionHandler(ClientAbortException.class)
	public void handleClientAbort(ClientAbortException e) {
		logger.debug("Client aborted connection: {}", e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleException(Exception e) {
		logger.error("Unhandled exception in FissureController", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Internal server error: " + e.getMessage());
	}

	@ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
	public ResponseEntity<FissureResponse> handleAsyncTimeout(
			org.springframework.web.context.request.async.AsyncRequestTimeoutException e) {
		logger.debug("Async request timeout caught by exception handler, returning empty response");
		FissureResponse response = new FissureResponse(Collections.emptyList(), Collections.emptySet());
		return ResponseEntity.ok(response);
	}
}