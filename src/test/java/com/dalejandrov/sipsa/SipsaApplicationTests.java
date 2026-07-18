package com.dalejandrov.sipsa;

import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SipsaApplicationTests {

	@Autowired
	private IngestionProperties ingestionProperties;

	@Test
	void contextLoads() {
	}

	/**
	 * Proves the real application context binds {@code sipsa.ingestion.batch-size}
	 * from YAML into {@link IngestionProperties}: the test profile pins 100 in
	 * {@code src/test/resources/application.yaml}, so seeing 100 here means the
	 * yaml → properties-class chain works end to end (TECH-071).
	 */
	@Test
	void ingestionBatchSizeBindsFromYaml() {
		assertThat(ingestionProperties.getBatchSize()).isEqualTo(100);
	}

	/**
	 * Same end-to-end proof for {@code sipsa.ingestion.monthly-window-start}
	 * (TECH-133): the test profile pins "00:00", so seeing midnight here means
	 * the yaml → typed LocalTime binding works in the real context.
	 */
	@Test
	void monthlyWindowStartBindsFromYaml() {
		assertThat(ingestionProperties.getMonthlyWindowStart()).isEqualTo(java.time.LocalTime.MIDNIGHT);
	}
}
