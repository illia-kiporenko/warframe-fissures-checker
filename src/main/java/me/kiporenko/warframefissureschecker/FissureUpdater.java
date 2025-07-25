package me.kiporenko.warframefissureschecker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Component
public class FissureUpdater {

	private static final Logger logger = LoggerFactory.getLogger(FissureUpdater.class);
	private static final String API_URL = "https://api.warframestat.us/pc/fissures/";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final int MAX_RETRIES = 3;

	private final FissureService fissureService;
	private final WebClient webClient;

	public FissureUpdater(FissureService fissureService) {
		this.fissureService = fissureService;
		this.webClient = WebClient.builder()
				.baseUrl("https://api.warframestat.us/pc")
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB buffer
				.build();
	}

	@Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
	public void fetchFissures() {
		logger.debug("Starting scheduled fissure fetch");

		webClient.get()
				.uri("/fissures/")
				.retrieve()
				.bodyToFlux(Fissure.class)
				.collectList()
				.timeout(TIMEOUT)
				.retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofSeconds(1))
						.filter(throwable -> throwable instanceof WebClientRequestException ||
								throwable instanceof WebClientResponseException))
				.doOnSuccess(fissures -> {
					logger.info("Successfully fetched {} fissures from API", fissures.size());
					fissureService.updateFissures(fissures);

					// Cleanup cache periodically
					fissureService.cleanupCache();
				})
				.doOnError(error -> {
					logger.error("Failed to fetch fissures from API after {} retries", MAX_RETRIES, error);
				})
				.onErrorResume(throwable -> {
					// On error, continue with empty list to avoid breaking the service
					logger.warn("Using empty fissure list due to API error");
					return Mono.just(List.of());
				})
				.subscribe();
	}

	// Manual trigger for testing/admin purposes
	public void fetchFissuresNow() {
		logger.info("Manual fissure fetch triggered");
		fetchFissures();
	}
}