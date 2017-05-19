package com.mploed.spring.events.applicationprocess.feed;

import com.mploed.spring.events.applicationprocess.domain.CreditApplicationStatus;
import com.mploed.spring.events.applicationprocess.repository.CreditApplicationStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.rometools.rome.feed.atom.Entry;
import com.rometools.rome.feed.atom.Feed;

import java.util.Date;

@Component
public class CreditDecisionPoller {
	private final Logger log = LoggerFactory.getLogger(CreditDecisionPoller.class);
	private final String FEED_URL = "http://localhost:9004/credit-decision/feed";

	private Date lastModified = null;

	CreditApplicationStatusRepository repository;

	RestTemplate restTemplate;

	@Autowired
	public CreditDecisionPoller(CreditApplicationStatusRepository repository, RestTemplate restTemplate) {
		this.repository = repository;
		this.restTemplate = restTemplate;
	}

	@Scheduled(fixedDelay = 10000)
	public void poll() {
		log.info("Starting Polling");

		HttpHeaders requestHeaders = new HttpHeaders();
		if (lastModified != null) {
			requestHeaders.set("If-Modified-Since", DateUtils.formatDate(lastModified));
		}
		HttpEntity<?> requestEntity = new HttpEntity(requestHeaders);
		ResponseEntity<Feed> response = restTemplate.exchange(FEED_URL, HttpMethod.GET, requestEntity, Feed.class);

		if (response.getStatusCode() != HttpStatus.NOT_MODIFIED) {
			Feed feed = response.getBody();
			Date lastUpdateInFeed = null;
			for (Entry entry : feed.getEntries()) {
				String applicationNumber = entry.getSummary().getValue();
				if ((lastModified == null) || (entry.getUpdated().after(lastModified))) {
					log.info(applicationNumber + " is new, updating the status");


					CreditApplicationStatus applicationStatus = repository.findByApplicationNumber(applicationNumber);
					if ((lastUpdateInFeed == null) || (entry.getUpdated().after(lastUpdateInFeed))) {
						lastUpdateInFeed = entry.getUpdated();
					}
					//orderRepository.save(order);
				}
			}
			if (response.getHeaders().getFirst("Last-Modified") != null) {
				lastModified = DateUtils.parseDate(response.getHeaders().getFirst("Last-Modified"));
				log.info("Last-Modified header {}", lastModified);
			} else {
				if (lastUpdateInFeed != null) {
					lastModified = lastUpdateInFeed;
					log.info("Last update in feed {}", lastModified);
				}

			}
		}
	}
}
