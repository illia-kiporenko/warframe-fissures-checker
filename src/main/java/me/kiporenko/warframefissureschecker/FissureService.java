package me.kiporenko.warframefissureschecker;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FissureService {

	private final List<Fissure> currentFissures = new ArrayList<>();
	private final Map<String, List<DeferredResult<List<Fissure>>>> listeners = new ConcurrentHashMap<>();

	public synchronized void registerListener(String missionType, DeferredResult<List<Fissure>> result) {
		List<Fissure> filtered = filterByMissionType(missionType);
		if (!filtered.isEmpty()) {
			result.setResult(filtered);
			return;
		}

		listeners.computeIfAbsent(missionType, k -> new ArrayList<>()).add(result);
	}

	public synchronized void updateFissures(List<Fissure> newFissures) {
		currentFissures.clear();
		currentFissures.addAll(newFissures);

		listeners.forEach((missionType, resultList) -> {
			List<Fissure> matching = filterByMissionType(missionType);
			if (!matching.isEmpty()) {
				for (DeferredResult<List<Fissure>> result : resultList) {
					result.setResult(matching);
				}
			}
		});
		System.out.println("Notifiyng");
		listeners.clear(); // clear after notifying
	}

	private List<Fissure> filterByMissionType(String type) {
		if (type == null) {
			return new ArrayList<>(currentFissures);
		}
		return currentFissures.stream()
				.filter(f -> f.getMissionType().equalsIgnoreCase(type))
				.collect(Collectors.toList());
	}
}
