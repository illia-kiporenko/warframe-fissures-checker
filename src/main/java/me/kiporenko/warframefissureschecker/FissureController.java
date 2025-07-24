package me.kiporenko.warframefissureschecker;

import me.kiporenko.warframefissureschecker.Fissure;
import me.kiporenko.warframefissureschecker.FissureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/fissures")
public class FissureController {

	private final FissureService fissureService;

	public FissureController(FissureService fissureService) {
		this.fissureService = fissureService;
	}

	@GetMapping
	public DeferredResult<List<Fissure>> getFissures(@RequestParam(required = false) String missionType) {

		DeferredResult<List<Fissure>> result = new DeferredResult<>(30_000L); // 30 sec timeout

		fissureService.registerListener(missionType, result);

		// If timeout occurs and nothing is available
		result.onTimeout(() -> result.setResult(Collections.emptyList()));

		return result;
	}
}
