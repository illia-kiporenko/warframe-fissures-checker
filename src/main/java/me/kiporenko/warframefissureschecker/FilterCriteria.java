package me.kiporenko.warframefissureschecker;

import java.util.List;
import java.util.Objects;

public class FilterCriteria {
	private final List<String> missionTypes;
	private final Boolean isHard;
	private final String cachedKey;  // Cache the key to avoid repeated string operations
	private final int cachedHashCode; // Cache hashcode for performance

	public FilterCriteria(List<String> missionTypes, Boolean isHard) {
		this.missionTypes = missionTypes;
		this.isHard = isHard;
		this.cachedKey = generateKey();
		this.cachedHashCode = Objects.hash(missionTypes, isHard);
	}

	public List<String> getMissionTypes() {
		return missionTypes;
	}

	public Boolean getIsHard() {
		return isHard;
	}

	public String getKey() {
		return cachedKey;
	}

	private String generateKey() {
		return String.format("types:%s,hard:%s",
				missionTypes != null && !missionTypes.isEmpty() ? String.join(",", missionTypes) : "all",
				isHard);
	}

	public boolean hasMissionTypeFilter() {
		return missionTypes != null && !missionTypes.isEmpty();
	}

	public boolean hasHardModeFilter() {
		return isHard != null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FilterCriteria that = (FilterCriteria) o;
		return Objects.equals(missionTypes, that.missionTypes) &&
				Objects.equals(isHard, that.isHard);
	}

	@Override
	public int hashCode() {
		return cachedHashCode;
	}

	@Override
	public String toString() {
		return "FilterCriteria{" +
				"missionTypes=" + missionTypes +
				", isHard=" + isHard +
				'}';
	}
}