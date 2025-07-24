package me.kiporenko.warframefissureschecker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FissureUpdater {

	private final FissureService fissureService;
	private final WebClient webClient = WebClient.create("https://api.warframestat.us/pc");

	public FissureUpdater(FissureService fissureService) {
		this.fissureService = fissureService;
	}

	@Scheduled(fixedRate = 10 * 60 * 1000) // every 10 minutes
	public void fetchFissures() {
		webClient.get()
				.uri("/fissures/")
				.retrieve()
				.bodyToFlux(Fissure.class)
				.collectList()
				.subscribe(fissureService::updateFissures);
	}
}
