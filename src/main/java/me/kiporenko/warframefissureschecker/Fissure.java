package me.kiporenko.warframefissureschecker;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@Data
public class Fissure {
	private String id;
	private Instant activation;
	private String startString;
	private Instant expiry;
	private boolean active;
	private String node;
	private String missionType;
	private String missionKey;
	private String enemy;
	private String enemyKey;
	private String nodeKey;
	private String tier;
	private int tierNum;
	private boolean expired;
	private String eta;
	@JsonProperty("isStorm")
	private boolean isStorm;
	@JsonProperty("isHard")
	private boolean isHard;

	// Add JsonProperty annotations if JSON field names differ from Java field names
	// Example:
	// @JsonProperty("mission_type")
	// private String missionType;
}