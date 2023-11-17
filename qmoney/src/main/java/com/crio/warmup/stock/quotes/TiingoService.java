
package com.crio.warmup.stock.quotes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.client.RestTemplate;

public class TiingoService implements StockQuotesService {

  private RestTemplate restTemplate;

  protected TiingoService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws JsonProcessingException {
        String url = buildUri(symbol, from, to);
        String candlesResponse = this.restTemplate.getForObject(url, String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return Arrays.asList(objectMapper.readValue(candlesResponse, TiingoCandle[].class));
  }

  protected static String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
    // 7a04e1b4e67ef19eb6ef84a7e545bb3b99921d08
    //e3f3ebbee3b2a7c335c5065488edd29356b01c92

    return "https://api.tiingo.com/tiingo/daily/" + symbol + "/prices?" + "startDate=" + startDate
        + "&endDate=" + endDate + "&token=" + "e3f3ebbee3b2a7c335c5065488edd29356b01c92";

  }

}
