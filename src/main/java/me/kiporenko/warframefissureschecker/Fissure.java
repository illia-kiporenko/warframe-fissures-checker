package me.kiporenko.warframefissureschecker;

import lombok.Data;

import java.time.Instant;
import java.time.ZonedDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Data
@Getter
@Setter
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
	private boolean isStorm;
	private boolean isHard;

	// геттеры и сеттеры

	// можно добавить @JsonProperty, если названия в JSON не совпадают
}

