package com.flamingo.ai.notebooklm.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for Elasticsearch client using Apache HttpComponents 5 (ES 9.0+). */
@Configuration
public class ElasticsearchConfig {

  @Value("${elasticsearch.host:localhost}")
  private String host;

  @Value("${elasticsearch.port:9200}")
  private int port;

  @Value("${elasticsearch.scheme:http}")
  private String scheme;

  @Bean
  public Rest5Client rest5Client() {
    HttpHost httpHost = new HttpHost(scheme, host, port);
    return Rest5Client.builder(httpHost).build();
  }

  @Bean
  public ElasticsearchTransport elasticsearchTransport(Rest5Client rest5Client) {
    return new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper());
  }

  @Bean
  public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
    return new ElasticsearchClient(transport);
  }
}
