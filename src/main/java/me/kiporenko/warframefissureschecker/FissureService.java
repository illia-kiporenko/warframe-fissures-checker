package me.kiporenko.warframefissureschecker;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class FissureService {

	private static final Logger logger = LoggerFactory.getLogger(FissureService.class);

	// Thread-safe collections
	private final List<Fissure> currentFissures = new CopyOnWriteArrayList<>();
	private final Map<String, List<ListenerInfo>> listeners = new ConcurrentHashMap<>();

	// Cache for filtered results to avoid repeated filtering
	private final Map<String, CachedFilterResult> filterCache = new ConcurrentHashMap<>();

	// Inner class to hold cached filter results
	private static class CachedFilterResult {
		final List<Fissure> fissures;
		final Set<String> fissureIds;
		final long timestamp;

		CachedFilterResult(List<Fissure> fissures, Set<String> fissureIds) {
			this.fissures = new ArrayList<>(fissures);
			this.fissureIds = new HashSet<>(fissureIds);
			this.timestamp = System.currentTimeMillis();
		}
	}

	// Inner class to hold listener info with expected data
	private static class ListenerInfo {
		final DeferredResult<List<Fissure>> result;
		final Set<String> expectedFissureIds;
		final FilterCriteria criteria;

		ListenerInfo(DeferredResult<List<Fissure>> result, Set<String> expectedFissureIds, FilterCriteria criteria) {
			this.result = result;
			this.expectedFissureIds = expectedFissureIds != null ? new HashSet<>(expectedFissureIds) : new HashSet<>();
			this.criteria = criteria;
		}
	}

	public void registerListener(FilterCriteria criteria, DeferredResult<List<Fissure>> result) {
		registerListener(criteria, result, null);
	}

	public synchronized void registerListener(FilterCriteria criteria, DeferredResult<List<Fissure>> result, Set<String> expectedFissureIds) {
		logger.info("Registering listener with criteria: {} and expected fissure IDs: {}", criteria, expectedFissureIds);

		// Get current matching fissures (using cache if available)
		List<Fissure> currentMatching = filterByCriteria(criteria);
		Set<String> currentIds = currentMatching.stream()
				.map(Fissure::getId)
				.collect(Collectors.toSet());

		// If no expected IDs provided (first request) or data has changed, return immediately
		if (expectedFissureIds == null || !currentIds.equals(expectedFissureIds)) {
			logger.debug("Data changed or first request. Current IDs: {}, Expected IDs: {}. Immediately returning {} fissures",
					currentIds, expectedFissureIds, currentMatching.size());
			result.setResult(currentMatching);
			return;
		}

		// Data hasn't changed, add to listeners for future updates
		String key = criteria.getKey();
		ListenerInfo listenerInfo = new ListenerInfo(result, expectedFissureIds, criteria);
		listeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listenerInfo);
		logger.debug("Data unchanged. Added listener to wait for updates. Total listeners for key '{}': {}",
				key, listeners.get(key).size());

		setupListenerCallbacks(key, listenerInfo, criteria);
	}

	private void setupListenerCallbacks(String key, ListenerInfo listenerInfo, FilterCriteria criteria) {
		listenerInfo.result.onCompletion(() -> removeCompletedListener(key, listenerInfo));

		listenerInfo.result.onTimeout(() -> {
			logger.debug("Request timed out for criteria: {}, returning current data", criteria);
			removeCompletedListener(key, listenerInfo);
			// On timeout, return current data (even if unchanged)
			if (!listenerInfo.result.isSetOrExpired()) {
				List<Fissure> timeoutData = filterByCriteria(criteria);
				listenerInfo.result.setResult(timeoutData);
			}
		});

		listenerInfo.result.onError(throwable -> {
			logger.debug("Request error for criteria: {}", criteria);
			removeCompletedListener(key, listenerInfo);
		});
	}

	public List<Fissure> getFissuresImmediate(FilterCriteria criteria) {
		logger.info("Getting immediate fissures for criteria: {}", criteria);
		List<Fissure> results = filterByCriteria(criteria);
		logger.debug("Found {} fissures immediately for criteria: {}", results.size(), criteria);
		return results;
	}

	public synchronized void updateFissures(List<Fissure> newFissures) {
		logger.info("Updating fissures. New count: {}, Previous count: {}",
				newFissures.size(), currentFissures.size());

		// Update fissure list
		currentFissures.clear();
		currentFissures.addAll(newFissures);

		// Clear filter cache since data changed
		filterCache.clear();

		notifyListeners();
	}

	private void notifyListeners() {
		if (listeners.isEmpty()) {
			return;
		}

		int totalNotified = 0;
		Iterator<Map.Entry<String, List<ListenerInfo>>> entryIterator = listeners.entrySet().iterator();

		while (entryIterator.hasNext()) {
			Map.Entry<String, List<ListenerInfo>> entry = entryIterator.next();
			String criteriaKey = entry.getKey();
			List<ListenerInfo> listenerInfos = entry.getValue();

			if (listenerInfos.isEmpty()) {
				entryIterator.remove();
				continue;
			}

			// Use the first listener's criteria (they should all be the same for the same key)
			FilterCriteria criteria = listenerInfos.get(0).criteria;
			List<Fissure> matching = filterByCriteria(criteria);
			Set<String> currentIds = matching.stream()
					.map(Fissure::getId)
					.collect(Collectors.toSet());

			logger.debug("Checking {} listeners for criteria key: {}. Current IDs: {}",
					listenerInfos.size(), criteriaKey, currentIds);

			Iterator<ListenerInfo> listenerIterator = listenerInfos.iterator();
			while (listenerIterator.hasNext()) {
				ListenerInfo listenerInfo = listenerIterator.next();

				// Check if data has changed compared to what this listener expects
				if (!currentIds.equals(listenerInfo.expectedFissureIds)) {
					logger.debug("Data changed for listener. Expected: {}, Current: {}",
							listenerInfo.expectedFissureIds, currentIds);

					if (!listenerInfo.result.isSetOrExpired()) {
						listenerInfo.result.setResult(matching);
						totalNotified++;
					}
					listenerIterator.remove(); // Remove after notifying
				} else {
					logger.debug("Data unchanged for listener, keeping in wait list");
				}
			}

			// Remove the entry if no listeners remain
			if (listenerInfos.isEmpty()) {
				entryIterator.remove();
			}
		}

		logger.info("Notified {} listeners about fissure updates. Remaining listeners: {}",
				totalNotified, getTotalListenerCount());
	}

	private void removeCompletedListener(String key, ListenerInfo listenerInfo) {
		List<ListenerInfo> listenerList = listeners.get(key);
		if (listenerList != null) {
			listenerList.remove(listenerInfo);
			if (listenerList.isEmpty()) {
				listeners.remove(key);
			}
		}
	}

	private List<Fissure> filterByCriteria(FilterCriteria criteria) {
		String cacheKey = criteria.getKey();

		// Check cache first
		CachedFilterResult cached = filterCache.get(cacheKey);
		if (cached != null) {
			// Cache is valid for a short time to avoid repeated filtering
			if (System.currentTimeMillis() - cached.timestamp < 1000) { // 1 second cache
				return new ArrayList<>(cached.fissures);
			}
		}

		// Filter and cache result
		List<Fissure> filtered = currentFissures.stream()
				.filter(fissure -> matchesMissionTypes(fissure, criteria.getMissionTypes()))
				.filter(fissure -> matchesHardMode(fissure, criteria.getIsHard()))
				.collect(Collectors.toList());

		Set<String> filteredIds = filtered.stream()
				.map(Fissure::getId)
				.collect(Collectors.toSet());

		filterCache.put(cacheKey, new CachedFilterResult(filtered, filteredIds));

		return filtered;
	}

	private boolean matchesMissionTypes(Fissure fissure, List<String> types) {
		if (types == null || types.isEmpty()) {
			return true;
		}

		String fissureMissionType = fissure.getMissionType();
		return types.stream()
				.anyMatch(type -> fissureMissionType.equalsIgnoreCase(type.trim()));
	}

	private boolean matchesHardMode(Fissure fissure, Boolean isHard) {
		return isHard == null || fissure.isHard() == isHard;
	}

	// Utility methods
	public List<Fissure> getCurrentFissures() {
		return new ArrayList<>(currentFissures);
	}

	public int getActiveListenerCount() {
		return getTotalListenerCount();
	}

	private int getTotalListenerCount() {
		return listeners.values().stream()
				.mapToInt(List::size)
				.sum();
	}

	public Set<String> getCurrentFissureIds(FilterCriteria criteria) {
		return filterByCriteria(criteria).stream()
				.map(Fissure::getId)
				.collect(Collectors.toSet());
	}

	// Clean up expired cache entries periodically (could be called by a scheduled task)
	public void cleanupCache() {
		long cutoff = System.currentTimeMillis() - 60000; // Remove entries older than 1 minute
		filterCache.entrySet().removeIf(entry -> entry.getValue().timestamp < cutoff);
	}
}