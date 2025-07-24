package me.kiporenko.warframefissureschecker;

import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

public class Main {
	public static void main(String[] args) {
		WebClient webClient = WebClient.create("https://api.warframestat.us/pc");

		List<Fissure> fissures = webClient.get()
				.uri("/fissures")
				.retrieve()
				.bodyToFlux(Fissure.class)
				.collectList()
				.block();

		System.out.println(fissures);
	}
}
